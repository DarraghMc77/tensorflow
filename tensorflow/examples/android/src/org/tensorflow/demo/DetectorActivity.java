/*
 * Copyright 2016 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.demo;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.BatteryManager;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements OnImageAvailableListener {
  private static final Logger LOGGER = new Logger();

  // Configuration values for the prepackaged multibox model.
  private static final int MB_INPUT_SIZE = 224;
  private static final int MB_IMAGE_MEAN = 128;
  private static final float MB_IMAGE_STD = 128;
  private static final String MB_INPUT_NAME = "ResizeBilinear";
  private static final String MB_OUTPUT_LOCATIONS_NAME = "output_locations/Reshape";
  private static final String MB_OUTPUT_SCORES_NAME = "output_scores/Reshape";
  private static final String MB_MODEL_FILE = "file:///android_asset/multibox_model.pb";
  private static final String MB_LOCATION_FILE =
          "file:///android_asset/multibox_location_priors.txt";

  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static final String TF_OD_API_MODEL_FILE =
          "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

  // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
  // must be manually placed in the assets/ directory by the user.
  // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
  // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
  // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
  private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
  private static final int YOLO_INPUT_SIZE = 416;
  private static final String YOLO_INPUT_NAME = "input";
  private static final String YOLO_OUTPUT_NAMES = "output";
  private static final int YOLO_BLOCK_SIZE = 32;

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
  // or YOLO.
  private enum DetectorMode {
    TF_OD_API, MULTIBOX, YOLO;
  }
  private static final DetectorMode MODE = DetectorMode.TF_OD_API;

  public enum OffloadingMode {
    LOCAL, SOCKET, HTTP
  }

  private static OffloadingMode OFF_MODE = OffloadingMode.LOCAL;

  // Minimum detection confidence to track a detection.
  private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.6f;
  private static final float MINIMUM_CONFIDENCE_MULTIBOX = 0.1f;
  private static final float MINIMUM_CONFIDENCE_YOLO = 0.25f;

  private static final boolean MAINTAIN_ASPECT = MODE == DetectorMode.TF_OD_API;

  private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);

  private static final boolean SAVE_PREVIEW_BITMAP = false;
  private static final float TEXT_SIZE_DIP = 10;

  private Integer sensorOrientation;

  private Classifier detector;

  private long lastProcessingTimeMs;
  private Bitmap rgbFrameBitmap = null;
  private Bitmap croppedBitmap = null;
  private Bitmap cropCopyBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private DetectionOffloadingClient doc = new DetectionOffloadingClient();
  private ReadVideo videoReader = new ReadVideo();
  private static int imageCount = 0;

  private static final Boolean testing = false;

  private static final Boolean enableTracking = true;

  private SimpleOffloadingDecision offloadingDecision = new SimpleOffloadingDecision();

  private final long LOCAL_PROCESSING_TIME = 600;

//  Intent myIntent = getIntent();
//  Intent otherIntent = getParentActivityIntent();
  int m = 0;
//  DetectorSettings detectorSettings = (DetectorSettings)intent.getSerializableExtra("detectorSettings");
//  private OffloadingMode OFF_MODE = detectorSettings.getOff_mode();

  private BorderedText borderedText;
  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation, DetectorSettings detectorSettings) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

    if(enableTracking){
      tracker = new MultiBoxTracker(this);
    }

    int cropSize = TF_OD_API_INPUT_SIZE;
    if (MODE == DetectorMode.YOLO) {
      detector =
              TensorFlowYoloDetector.create(
                      getAssets(),
                      YOLO_MODEL_FILE,
                      YOLO_INPUT_SIZE,
                      YOLO_INPUT_NAME,
                      YOLO_OUTPUT_NAMES,
                      YOLO_BLOCK_SIZE);
      cropSize = YOLO_INPUT_SIZE;
    } else if (MODE == DetectorMode.MULTIBOX) {
      detector =
              TensorFlowMultiBoxDetector.create(
                      getAssets(),
                      MB_MODEL_FILE,
                      MB_LOCATION_FILE,
                      MB_IMAGE_MEAN,
                      MB_IMAGE_STD,
                      MB_INPUT_NAME,
                      MB_OUTPUT_LOCATIONS_NAME,
                      MB_OUTPUT_SCORES_NAME);
      cropSize = MB_INPUT_SIZE;
    } else {
      try {
        detector = TensorFlowObjectDetectionAPIModel.create(
                getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, TF_OD_API_INPUT_SIZE);
        cropSize = TF_OD_API_INPUT_SIZE;
      } catch (final IOException e) {
        LOGGER.e("Exception initializing classifier!", e);
        Toast toast =
                Toast.makeText(
                        getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
        toast.show();
        finish();
      }
    }

    previewWidth = size.getWidth();
    previewHeight = size.getHeight();

    if(testing){
      sensorOrientation = 0;
    }
    else{
      sensorOrientation = rotation - getScreenOrientation();
    }

    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888); //TODO:: maybe change this to RGB_565 for performance increase

    if(testing){
      frameToCropTransform =
              ImageUtils.getTransformationMatrix(
                      300, 300,
                      cropSize, cropSize,
                      sensorOrientation, MAINTAIN_ASPECT);
    }
    else{
      frameToCropTransform =
              ImageUtils.getTransformationMatrix(
                      previewWidth, previewHeight,
                      cropSize, cropSize,
                      sensorOrientation, MAINTAIN_ASPECT);
    }

    cropToFrameTransform = new Matrix();
    frameToCropTransform.invert(cropToFrameTransform);

    if(enableTracking){
      trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
      trackingOverlay.addCallback(
              new DrawCallback() {
                @Override
                public void drawCallback(final Canvas canvas) {
                  tracker.draw(canvas);
                  if (isDebug()) {
                    tracker.drawDebug(canvas);
                  }
                }
              });
    }

    addCallback(
            new DrawCallback() {
              @Override
              public void drawCallback(final Canvas canvas) {
                if (!isDebug()) {
                  return;
                }
                final Bitmap copy = cropCopyBitmap;
                if (copy == null) {
                  return;
                }

                final int backgroundColor = Color.argb(100, 0, 0, 0);
                canvas.drawColor(backgroundColor);

                final Matrix matrix = new Matrix();
                final float scaleFactor = 2;
                matrix.postScale(scaleFactor, scaleFactor);
                matrix.postTranslate(
                        canvas.getWidth() - copy.getWidth() * scaleFactor,
                        canvas.getHeight() - copy.getHeight() * scaleFactor);
                canvas.drawBitmap(copy, matrix, new Paint());

                final Vector<String> lines = new Vector<String>();
                if (detector != null) {
                  final String statString = detector.getStatString();
                  final String[] statLines = statString.split("\n");
                  for (final String line : statLines) {
                    lines.add(line);
                  }
                }
                lines.add("");

                lines.add("Frame: " + previewWidth + "x" + previewHeight);
                lines.add("Crop: " + copy.getWidth() + "x" + copy.getHeight());
                lines.add("View: " + canvas.getWidth() + "x" + canvas.getHeight());
                lines.add("Rotation: " + sensorOrientation);
                lines.add("Inference time: " + lastProcessingTimeMs + "ms");

                borderedText.drawLines(canvas, 10, canvas.getHeight() - 10, lines);
              }
            });
  }

  OverlayView trackingOverlay;

  @Override
  protected void processImage() {

    Intent batteryIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    LOGGER.i("BATTERY LEVEL: " + level);
    LOGGER.i("SCALE:  " + scale);

    ArrayList<Bitmap> gt_images = new ArrayList<Bitmap>();
    if (testing) {
        try {
          gt_images.add(videoReader.readVideo(getApplicationContext(), imageCount));
        }
        catch (Exception e){
          System.out.println("err");
          LOGGER.i("NEW ERROR: " + e.getMessage());
        }

      if(imageCount < 629){
        imageCount++;
      }
    }

    ++timestamp;
    final long currTimestamp = timestamp;
    byte[] originalLuminance = getLuminance();

    if(enableTracking){
      tracker.onFrame(
              previewWidth,
              previewHeight,
              getLuminanceStride(),
              sensorOrientation,
              originalLuminance,
              timestamp);
      trackingOverlay.postInvalidate();
    }

    // No mutex needed as this method is not reentrant.
    if (computingDetection) {
      readyForNextImage();
      return;
    }
    computingDetection = true;
    LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

    rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

    if (luminanceCopy == null) {
      luminanceCopy = new byte[originalLuminance.length];
    }
    System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);
    readyForNextImage();

//    Testing
    if(testing) {
      final Canvas canvas = new Canvas(croppedBitmap);
      canvas.drawBitmap(gt_images.get(0), frameToCropTransform, null);
    }
    else{
      final Canvas canvas = new Canvas(croppedBitmap);
      canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
    }

    // For examining the actual TF input.
    if (SAVE_PREVIEW_BITMAP) {
      ImageUtils.saveBitmap(croppedBitmap);
    }

    runInBackground(
            new Runnable() {
              @Override
              public void run() {

                if(testing){
                  DetectorTestActivity dta = new DetectorTestActivity();
                  dta.testImages(getApplicationContext(), detector);
                }

                LOGGER.i("Running detection on image " + currTimestamp);
                final long startTime = SystemClock.uptimeMillis();

                List<Classifier.Recognition> results = new ArrayList<Classifier.Recognition>();

//                OFF_MODE = offloadingDecision.makeDecision(lastProcessingTimeMs, LOCAL_PROCESSING_TIME);

                if(detectorSettings.getOffloadingMode() == OffloadingMode.LOCAL){
                  LOGGER.i("LOCAL Processing mode");
                  results = detector.recognizeImage(croppedBitmap);
                  LOGGER.i(results.toString());
                }
                else if(detectorSettings.getOffloadingMode() == OffloadingMode.HTTP) {
                  LOGGER.i("HTTP MODE");
                  List<OffloadingClassifierResult> serverResult;
                  try{
                    serverResult = doc.postImage(croppedBitmap, startTime);
                    LOGGER.i("Recieved Result End: " + (SystemClock.uptimeMillis() - startTime));
                  }
                  catch (Exception e){
                    serverResult = null;
                    LOGGER.i("ERROR: Unable to connect to the server");
                  }

                  long startTime3 = SystemClock.uptimeMillis();

                  // TODO: use java 8 streams .map() - slower don't

                  for (OffloadingClassifierResult result: serverResult) {
                    final RectF boundingBox =
                            new RectF(
                                    Math.max(0, result.getTopleft().getX() - result.getBottomRight().getX() / 2),
                                    Math.max(0, result.getTopleft().getY() - result.getBottomRight().getY() / 2),
                                    Math.min(300 - 1, result.getTopleft().getX() + result.getBottomRight().getX() / 2),
                                    Math.min(300 - 1, result.getTopleft().getY() + result.getBottomRight().getY() / 2));
                    results.add(new Classifier.Recognition("Prediction ", result.getLabel(), result.getConfidence(), boundingBox));
                  }

                  LOGGER.i("Converted to result: " + (SystemClock.uptimeMillis() - startTime3));
                }
                else if(detectorSettings.getOffloadingMode() == OffloadingMode.SOCKET){
                  LOGGER.i("Offloading using socket connection");
//                  OffloadingClassifierResult serverResult = off_socket_client.sendBitmap(croppedBitmap, socket);
//                  final RectF boundingBox = new RectF(serverResult.getTopleft().getX(), serverResult.getTopleft().getY(), serverResult.getBottomRight().getX(), serverResult.getBottomRight().getY());
//                  results.add(new Classifier.Recognition("Prediction ", serverResult.getLabel(), serverResult.getConfidence(), boundingBox));
                }

                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                  case TF_OD_API:
                    minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                    break;
                  case MULTIBOX:
                    minimumConfidence = MINIMUM_CONFIDENCE_MULTIBOX;
                    break;
                  case YOLO:
                    minimumConfidence = MINIMUM_CONFIDENCE_YOLO;
                    break;
                }

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                List<OffloadingClassifierResult> testResults = new ArrayList<OffloadingClassifierResult>();

                for (final Classifier.Recognition result : results) {
                  final RectF location = result.getLocation();
                  if (location != null && result.getConfidence() >= minimumConfidence) {

                    if(testing){
                      OffloadingClassifierResult newResult = new OffloadingClassifierResult();
                      newResult.setLabel(result.getTitle());
                      newResult.setConfidence(result.getConfidence());
                      OffloadingClassifierResult.Coordinate bottomRight = new OffloadingClassifierResult.Coordinate();
                      bottomRight.setX(result.getLocation().right);
                      bottomRight.setY(result.getLocation().bottom);
                      newResult.setBottomRight(bottomRight);


                      OffloadingClassifierResult.Coordinate topLeft = new OffloadingClassifierResult.Coordinate();
                      topLeft.setX(result.getLocation().left);
                      topLeft.setY(result.getLocation().top);
                      newResult.setTopleft(topLeft);

                      newResult.setImageNumber(imageCount);

                      testResults.add(newResult);
                    }
                    canvas.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                  }
                }

                if(testing){
                  try{
                    int resp = doc.postResult(testResults, imageCount);
                  }
                  catch (Exception e){
                    LOGGER.i(e.getMessage());
                  }
                }

                if(enableTracking){
                  tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                  trackingOverlay.postInvalidate();
                }

                requestRender();
                computingDetection = false;
              }
            });
  }

  @Override
  protected int getLayoutId() {
    return R.layout.camera_connection_fragment_tracking;
  }

  @Override
  protected Size getDesiredPreviewFrameSize() {
    return DESIRED_PREVIEW_SIZE;
  }

  @Override
  public void onSetDebug(final boolean debug) {
    detector.enableStatLogging(debug);
  }
}
