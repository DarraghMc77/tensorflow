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

import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.os.Environment;
import android.os.SystemClock;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;

import org.tensorflow.demo.OverlayView.DrawCallback;
import org.tensorflow.demo.env.BorderedText;
import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
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

//  private static final int TF_OD_API_INPUT_SIZE = 300;
  private static int TF_OD_API_INPUT_SIZE = 300;
  private static final String TF_OD_API_MODEL_FILE =
          "file:///android_asset/ssd_mobilenet_v1_android_export.pb";
  private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/coco_labels_list.txt";

  // Configuration values for tiny-yolo-voc. Note that the graph is not included with TensorFlow and
  // must be manually placed in the assets/ directory by the user.
  // Graphs and models downloaded from http://pjreddie.com/darknet/yolo/ may be converted e.g. via
  // DarkFlow (https://github.com/thtrieu/darkflow). Sample command:
  // ./flow --model cfg/tiny-yolo-voc.cfg --load bin/tiny-yolo-voc.weights --savepb --verbalise
  private static final String YOLO_MODEL_FILE = "file:///android_asset/graph-tiny-yolo-voc.pb";
  private static final int YOLO_INPUT_SIZE = 352;

  private static final String YOLO_INPUT_NAME = "input";
  private static final String YOLO_OUTPUT_NAMES = "output";
  private static final int YOLO_BLOCK_SIZE = 32;

  // Which detection model to use: by default uses Tensorflow Object Detection API frozen
  // checkpoints.  Optionally use legacy Multibox (trained using an older version of the API)
  // or YOLO.
  private enum DetectorMode {
    TF_OD_API, MULTIBOX, YOLO;
  }
  private static final DetectorMode MODE = DetectorMode.YOLO;

  public enum OffloadingMode {
    LOCAL, SOCKET, HTTP, TRACKING
  }

  private static OffloadingMode OFF_MODE;

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
  private Bitmap sceneChangeBitmap = null;

  private boolean computingDetection = false;

  private long timestamp = 0;

  private Matrix frameToCropTransform;
  private Matrix cropToFrameTransform;

  private MultiBoxTracker tracker;

  private byte[] luminanceCopy;

  private DetectionOffloadingClient doc = new DetectionOffloadingClient();
  private ReadVideo videoReader = new ReadVideo();
  private static int imageCount = 0;
  private Bitmap previousFrame = null;
  public static Boolean trackingFailure = false;

  private SimpleOffloadingDecision offloadingDecision = new SimpleOffloadingDecision();

  private final long LOCAL_PROCESSING_TIME = 600;

  static NetworkContext networkContext;
  static SystemContext systemContext;

  private int batteryLevel;
  private IntentFilter mBatteryLevelFilter;

  private long detectionCount = 0;
  private long averageInferanceTime = 0;
  private long totalInferanceTime = 0;

  private long detectionCount2 = 0;
  private long averageInferanceTime2 = 0;
  private long totalInferanceTime2 = 0;

  private OffloadingMode defaultMode;

  public static BufferedWriter out;
  Boolean croppedScene;

  BroadcastReceiver mBatteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
      systemContext.setBatteryLevel(batteryLevel);
      Date date = new Date();
      try {
        writeToFile("BATTERY LEVEL: " + batteryLevel + "logged at" + String.valueOf(date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds() + "\n"));
      }
      catch(Exception e){
        System.out.println(e.toString());
      }
      LOGGER.i("Battery Level: " + batteryLevel);
    }
  };

  private void registerMyReceiver() {
    mBatteryLevelFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    registerReceiver(mBatteryReceiver, mBatteryLevelFilter);
  }

  private BorderedText borderedText;
  @Override
  public void onPreviewSizeChosen(final Size size, final int rotation, DetectorSettings detectorSettings) {
    final float textSizePx =
            TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
    borderedText = new BorderedText(textSizePx);
    borderedText.setTypeface(Typeface.MONOSPACE);

//    registerMyReceiver();

    OFF_MODE = detectorSettings.getOffloadingMode();
    //use this
    defaultMode = detectorSettings.getOffloadingMode();

//    try {
//      createFileOnDevice(true);
//    }
//    catch(Exception e){
//      System.out.println(e.toString());
//    }

    if(detectorSettings.getEnableTracking()){
      tracker = new MultiBoxTracker(this);
    }

    int cropSize = TF_OD_API_INPUT_SIZE;
    if (MODE == DetectorMode.YOLO) {
      detector =
              TensorFlowYoloDetector.create(
                      getAssets(),
                      YOLO_MODEL_FILE,
                      detectorSettings.getResolution(),
                      YOLO_INPUT_NAME,
                      YOLO_OUTPUT_NAMES,
                      YOLO_BLOCK_SIZE);
      cropSize = detectorSettings.getResolution();
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
                getAssets(), TF_OD_API_MODEL_FILE, TF_OD_API_LABELS_FILE, detectorSettings.getResolution());
        cropSize = detectorSettings.getResolution();
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

    if(detectorSettings.getTesting()){
      sensorOrientation = 0;
    }
    else{
      sensorOrientation = rotation - getScreenOrientation();
    }

    if(detectorSettings.getTesting()){
      DetectorTestActivity dta = new DetectorTestActivity();
      dta.testImages(getApplicationContext(), detector, detectorSettings.getResolution());
    }

    LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

    LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
    rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
    croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888); //TODO:: maybe change this to RGB_565 for performance increase

    if(detectorSettings.getTesting()){
      frameToCropTransform =
              ImageUtils.getTransformationMatrix(
                      352, 352,
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

    if(detectorSettings.getEnableTracking()){
      trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
      trackingOverlay.addCallback(
              new DrawCallback() {
                @Override
                public void drawCallback(final Canvas canvas) {
                  tracker.draw(canvas, imageCount, lastProcessingTimeMs, OFF_MODE.toString());
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
//                if (detector != null) {
//                  final String statString = detector.getStatString();
//                  final String[] statLines = statString.split("\n");
//                  for (final String line : statLines) {
//                    lines.add(line);
//                  }
//                }
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

    OFF_MODE = detectorSettings.getOffloadingMode();

    ArrayList<Bitmap> gt_images = new ArrayList<Bitmap>();
    if (detectorSettings.getTesting()) {
      try {
        gt_images.add(videoReader.readVideo(getApplicationContext(), imageCount));
      } catch (Exception e) {
        LOGGER.i("NEW ERROR: " + e.getMessage());
      }

      if (imageCount <= 630) {
        imageCount++;
      }
      else{
        imageCount = 0;
      }
    }

//    UpdateNetworkStatus(this, "http://192.168.6.131:5010/test_download", "http://192.168.6.131:5010/test_upload");

    ++timestamp;
    final long currTimestamp = timestamp;
//    LOGGER.i("TRACKING TIMESTAMP " + currTimestamp);



    byte[] originalLuminance = getLuminance();

    final long startTime = SystemClock.uptimeMillis();
    if(detectorSettings.getEnableTracking()){
      tracker.onFrame(
              previewWidth,
              previewHeight,
              getLuminanceStride(),
              sensorOrientation,
              getLuminance(),
              timestamp,
              imageCount);
      trackingOverlay.postInvalidate();
    }

    LOGGER.i("TRACKING TIME: " + (SystemClock.uptimeMillis() - startTime));
    long trackingTime = SystemClock.uptimeMillis() - startTime;

    if(detectorSettings.getOffloadingMode() == OffloadingMode.TRACKING){
      lastProcessingTimeMs = trackingTime;
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
    if(detectorSettings.getTesting()) {
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

                // for use with decision model
//                detector.setInputSize(detectorSettings.getResolution());

//                trackingDecision();
                if(detectorSettings.getTrackingDecision() && detectorSettings.getEnableTracking() && previousFrame != null) { // & isTrackingObjects
                  long frameDifference = ImageUtils.getDifferencePercent(croppedBitmap, previousFrame);
                  LOGGER.i("FRAME DIFFERENCE: " + frameDifference);
                  if (frameDifference < 10) {
                    detectorSettings.setOffloadingMode(OffloadingMode.TRACKING);
                  } else {
                    detectorSettings.setOffloadingMode(defaultMode);
                  }
                }

                croppedScene = false;

                LOGGER.i("OFFLOADING MODE: "+ detectorSettings.getOffloadingMode().toString());

//                OFF_MODE = offloadingDecision.makeDecision(networkContext);

                LOGGER.i("Running detection on image " + currTimestamp);
                long startTime = SystemClock.uptimeMillis();
                List<Classifier.Recognition> results = new ArrayList<>();

                if(detectorSettings.getOffloadingMode() == OffloadingMode.HTTP) {
                  LOGGER.i("HTTP MODE");
                  List<OffloadingClassifierResult> serverResult;
                  try{
//                    byte[] byteImage = getBytesFromBitmap(croppedBitmap);
//                long lengthImage = byteImage.length;
                    serverResult = doc.postImage(croppedBitmap, 2.5);
//                    LOGGER.i("BANDWIDTH: " + (7000*8*1000)/(1024*1024*(SystemClock.uptimeMillis() - startTime)));
                  }
                  catch(IOException e){
                    LOGGER.i("TIMEOUT: Switching to local processing: " + e);
                    serverResult = new ArrayList<>();
                    detectorSettings.setOffloadingMode(OffloadingMode.LOCAL);
                  }
                  catch (Exception e){
                    serverResult = new ArrayList<>();
                    LOGGER.i("ERROR: Unable to connect to the server");
                  }

                  long startTime3 = SystemClock.uptimeMillis();

                  for (OffloadingClassifierResult result: serverResult) {
                    final RectF boundingBox =
                            new RectF(
                                    result.getTopleft().getX(),
                                    result.getTopleft().getY(),
                                    result.getBottomRight().getX(),
                                    result.getBottomRight().getY());
                    results.add(new Classifier.Recognition("Prediction ", result.getLabel(), result.getConfidence(), boundingBox));
                  }

                  LOGGER.i("Converted to result: " + (SystemClock.uptimeMillis() - startTime3));

                }
                else if(detectorSettings.getOffloadingMode() == OffloadingMode.LOCAL){
                  LOGGER.i("LOCAL Processing mode");
                  if(croppedScene){
                    results = detector.recognizeImage(sceneChangeBitmap);
                  }
                  else{
                    results = detector.recognizeImage(croppedBitmap);
                  }
                  LOGGER.i(results.toString());
                }
                else if(detectorSettings.getOffloadingMode() == OffloadingMode.SOCKET){
                  LOGGER.i("Offloading using socket connection");
//                  OffloadingClassifierResult serverResult = off_socket_client.sendBitmap(croppedBitmap, socket);
//                  final RectF boundingBox = new RectF(serverResult.getTopleft().getX(), serverResult.getTopleft().getY(), serverResult.getBottomRight().getX(), serverResult.getBottomRight().getY());
//                  results.add(new Classifier.Recognition("Prediction ", serverResult.getLabel(), serverResult.getConfidence(), boundingBox));
                }

                detectionCount++;
                if(detectionCount >= 2){
                  if(detectorSettings.getOffloadingMode() != OffloadingMode.TRACKING){
                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                  }
                  LOGGER.i("INFERENCE TIME: " + lastProcessingTimeMs);
                  totalInferanceTime += lastProcessingTimeMs;

                  averageInferanceTime = totalInferanceTime / detectionCount;
                  LOGGER.i("AVERAGE INFERANCE: " + averageInferanceTime);
                }

                detectionCount2++;

                if(detectorSettings.getOffloadingMode() != OffloadingMode.TRACKING){
                  lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
                }
                LOGGER.i("INFERENCE TIME2: " + lastProcessingTimeMs);
                totalInferanceTime2 += lastProcessingTimeMs;

                averageInferanceTime2 = totalInferanceTime2 / detectionCount2;
                LOGGER.i("AVERAGE INFERANCE2: " + averageInferanceTime2);


                previousFrame = Bitmap.createBitmap(croppedBitmap);
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

//                    if(detectorSettings.getTesting()){
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
//                    }
                    canvas.drawRect(location, paint);

                    cropToFrameTransform.mapRect(location);
                    result.setLocation(location);
                    mappedRecognitions.add(result);
                  }
                }

//                try{
//                  ObjectMapper mapper = new ObjectMapper();
//                  String json_convert = mapper.writeValueAsString(testResults);
//                  writeToFile(json_convert);
//                }
//                catch(Exception e){
//                  System.out.println("here");
//                }
//
//                if(detectorSettings.getTesting()){
//                  try{
//                    int resp = doc.postResult(testResults, imageCount);
//                  }
//                  catch (Exception e){
//                    LOGGER.i(e.getMessage());
//                  }
//                }

                if(detectorSettings.getEnableTracking()){
                  LOGGER.i("DETECTION TIMESTAMP " + currTimestamp);
                  tracker.trackResults(mappedRecognitions, luminanceCopy, currTimestamp);
                  trackingOverlay.postInvalidate();
                }

//                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
//                LOGGER.i("INFERENCE TIME: " + lastProcessingTimeMs);

                requestRender();
                computingDetection = false;
              }
            });
  }

  public static void UpdateNetworkStatus(Context context, String downUrl,
                                         String upUrl) {
    if (networkContext == null) {
      networkContext = NetworkContext.getInstance();
    }
    networkContext.UpdateNetworkContext(context);
    LOGGER.i("DOWNLOAD BW: " + networkContext.getDownloadBw());
    LOGGER.i("UPLOAD BW: " + networkContext.getUploadBw());
  }

  public static void UpdateSystemContext(Context context) {
    if (systemContext == null) {
      systemContext = systemContext.getInstance();
    }
    systemContext.UpdateSystemContext(context);
  }

  private void createFileOnDevice(Boolean append) throws IOException {
                /*
                 * Function to initially create the log file and it also writes the time of creation to file.
                 */
    File Root = Environment.getExternalStorageDirectory();
    if(Root.canWrite()){
      File  LogFile = new File(Root, "DetectLog.txt");
      FileWriter LogWriter = new FileWriter(LogFile, append);
      out = new BufferedWriter(LogWriter);
//      Date currentTime = Calendar.getInstance().getTime();
      Date date = new Date();
      out.write("Logged at" + String.valueOf(date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds() + "\n"));
      out.close();

    }
  }

  private void writeToFile(String message) throws IOException {
                /*
                 * Function to initially create the log file and it also writes the time of creation to file.
                 */
    File Root = Environment.getExternalStorageDirectory();
    if(Root.canWrite()){
      File  LogFile = new File(Root, "DetectLog.txt");
      FileWriter LogWriter = new FileWriter(LogFile, true);
      out = new BufferedWriter(LogWriter);
//      Date currentTime = Calendar.getInstance().getTime();
      Date date = new Date();
      out.write(message + "\n");
      out.close();

    }
  }

  private void populateYUVLuminanceFromRGB(int[] rgb, byte[] yuv420sp, int width, int height) {
    for (int i = 0; i < width * height; i++) {
      float red = (rgb[i] >> 16) & 0xff;
      float green = (rgb[i] >> 8) & 0xff;
      float blue = (rgb[i]) & 0xff;
      int luminance = (int) ((0.257f * red) + (0.504f * green) + (0.098f * blue) + 16);
      yuv420sp[i] = (byte) (0xff & luminance);
    }
  }

  public void writeToFile_(String message) {
    try {
      out.write(message + "\n");
      out.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public byte[] getBytesFromBitmap(Bitmap bitmap) {
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
    return stream.toByteArray();
  }

  // cropping of the frame when offloading removed for the purposes of testing
  public void trackingDecision(){
    double frameDifference = 0.0;

    LOGGER.i("TRACKING FAILURE: "+ trackingFailure);
    if(!trackingFailure){
      detectorSettings.setOffloadingMode(OffloadingMode.TRACKING);
    }
    else{
      detectorSettings.setOffloadingMode(OffloadingMode.LOCAL);
    }

    croppedScene = false;

    if(detectorSettings.getEnableTracking() && previousFrame != null) {

      int width = croppedBitmap.getWidth();
      int height = croppedBitmap.getHeight();
      long frameDifferences = ImageUtils.getDifferencePercent(croppedBitmap, previousFrame);

      int height2 = croppedBitmap.getHeight();
//      List<Integer> sectorChange = new ArrayList<>();
//
//      for(int i = 0; i < frameDifferences.length - 1; i++){
//        if(frameDifferences[i] > 10){
//          sectorChange.add(i);
//        }
//      }
//
//      frameDifference = frameDifferences[4];
//      LOGGER.i("FRAME DIFFERENCE: "+ frameDifference + "%");
//
//      int halfWidth = width / 2;
//      int halfHeight = height / 2;
//
//      if(sectorChange.size() == 1 && sectorChange.get(0) == 0){
//        sceneChangeBitmap = Bitmap.createBitmap(croppedBitmap, 0, 0, halfWidth, halfHeight);
//        croppedScene = true;
//      }
//      else if(sectorChange.size() == 1 && sectorChange.get(0) == 1){
//        sceneChangeBitmap = Bitmap.createBitmap(croppedBitmap, halfWidth, 0, halfWidth, halfHeight);
//        croppedScene = true;
//      }
//      else if(sectorChange.size() == 1 && sectorChange.get(0) == 2){
//        sceneChangeBitmap = Bitmap.createBitmap(croppedBitmap, 0, halfHeight, halfWidth, halfHeight);
//        croppedScene = true;
//      }
//      else if(sectorChange.size() == 1 && sectorChange.get(0) == 3){
//        sceneChangeBitmap = Bitmap.createBitmap(croppedBitmap, halfWidth, halfHeight, halfWidth, halfHeight);
//        croppedScene = true;
//      }

    }

//    LOGGER.i("FRAME DIFFERENCE TIME: " + (startTime - SystemClock.uptimeMillis()));

    if(frameDifference > 10){
      detectorSettings.setOffloadingMode(OffloadingMode.TRACKING);
    }
    else{
      detectorSettings.setOffloadingMode(OffloadingMode.HTTP);
      trackingFailure = false;
    }
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
