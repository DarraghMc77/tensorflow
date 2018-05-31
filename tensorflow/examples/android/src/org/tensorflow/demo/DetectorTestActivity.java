package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.os.Environment;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;
import org.tensorflow.demo.tracking.MultiBoxTracker;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by Darragh on 16/04/2018.
 *
 *
 * Testing interface for emulating application with results written into DetectTestLog.txt file and stored in memory
 * Delay is introduced and tested by running the detection a number of frames behind the tracker (3 frames = 100ms delay for a 30fps camera feed)
 *
 */

public class DetectorTestActivity {

    private ReadVideo videoReader = new ReadVideo();
    int numberOfImages = 630;
    private static final Logger LOGGER = new Logger();
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap croppedBitmapDelay = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    float minimumConfidence = 0.25f;
    private DetectionOffloadingClient doc = new DetectionOffloadingClient();
    private Bitmap previousFrame = null;
    private MultiBoxTracker tracker;
    private long timestamp = 0;
    private byte[] luminanceCopy;
    private Matrix frameToCanvasMatrix;

    private static final boolean MAINTAIN_ASPECT = true;

    private int detectionDelay = 0;

    public static BufferedWriter outTest;

    // MAYBE GRAB IMAGE 3 FRAMES AHEAD
    public void testImages(Context context, Classifier detector, int cropSize){
        detector.setInputSize(cropSize);
        tracker = new MultiBoxTracker(context);

        //mapping results of lower resolutions back to original resolution
        final boolean rotated = 0 % 180 == 90;

        //for mapping results from cropped image back to original size
        final float multiplier =
                Math.min(352 / (float) (rotated ? cropSize : cropSize),
                        352 / (float) (rotated ? cropSize : cropSize));
        frameToCanvasMatrix =
                ImageUtils.getTransformationMatrix(
                        cropSize,
                        cropSize,
                        (int) (multiplier * (rotated ? cropSize : cropSize)),
                        (int) (multiplier * (rotated ? cropSize : cropSize)),
                        0,
                        false);

        try {
            createFileOnDevice(true);
        }
        catch(Exception e){
            System.out.println(e.toString());
        }

        for(int i=0; i < numberOfImages; i++){
            LOGGER.i("IMAGE NUMBER: " + String.valueOf(i));
            rgbFrameBitmap = Bitmap.createBitmap(352, 352, Config.ARGB_8888);
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);
            croppedBitmapDelay = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            352, 352,
                            cropSize, cropSize,
                            0, MAINTAIN_ASPECT);

            Bitmap gtFrame = null;
            try {
                gtFrame = ImageUtils.readGtImage(i);
            }
            catch (Exception e){
                System.out.println("err");
                LOGGER.i("NEW ERROR: " + e.getMessage());
            }

            final Canvas canvas = new Canvas(croppedBitmap);
            canvas.drawBitmap(gtFrame, frameToCropTransform, null);

//            int w = gt_images.get(0).getWidth(), h = gt_images.get(0).getHeight();
//            int w = croppedBitmap.getWidth(), h = croppedBitmap.getHeight();
//
//
//            int[] rgb = new int[w * h];
//            byte[] yuv = new byte[w * h];
//
//            croppedBitmap.getPixels(rgb, 0, w, 0, 0, w, h);
//            populateYUVLuminanceFromRGB(rgb, yuv, w, h);
//
//            ++timestamp;
//            final long currTimestamp = timestamp;
//            tracker.onFrame(
//                    352,
//                    352,
//                    352,
//                    0,
//                    yuv,
//                    timestamp,
//                    i);
//
//            if (luminanceCopy == null) {
//                luminanceCopy = new byte[yuv.length];
//            }
//            System.arraycopy(yuv, 0, luminanceCopy, 0, yuv.length);


            if(i > 2) {
//                double frameDifference = videoReader.getDifferencePercent(croppedBitmap, previousFrame);
//                LOGGER.i("FRAME DIFFERENCE: image: " + i + "difference: "+ frameDifference);
            }

            if(i >= detectionDelay){
                Bitmap gtFrame2 = null;
                try {
                    gtFrame2 = ImageUtils.readGtImage(i - detectionDelay);
                }
                catch (Exception e){
                    System.out.println("err");
                    LOGGER.i("NEW ERROR: " + e.getMessage());
                }

                final Canvas canvas2 = new Canvas(croppedBitmapDelay);
                canvas2.drawBitmap(gtFrame2, frameToCropTransform, null);

                List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmapDelay);

                List<OffloadingClassifierResult> testResults = new ArrayList<OffloadingClassifierResult>();;

//            for (final Classifier.Recognition result : results) {
//                final RectF location = result.getLocation();
//                if (location != null && result.getConfidence() >= minimumConfidence) {
//
//                    OffloadingClassifierResult newResult = new OffloadingClassifierResult();
//                    newResult.setLabel(result.getTitle());
//                    newResult.setConfidence(result.getConfidence());
//                    OffloadingClassifierResult.Coordinate bottomRight = new OffloadingClassifierResult.Coordinate();
//                    bottomRight.setX(result.getLocation().right);
//                    bottomRight.setY(result.getLocation().bottom);
//                    newResult.setBottomRight(bottomRight);
//
//
//                    OffloadingClassifierResult.Coordinate topLeft = new OffloadingClassifierResult.Coordinate();
//                    topLeft.setX(result.getLocation().left);
//                    topLeft.setY(result.getLocation().top);
//                    newResult.setTopleft(topLeft);
//
//                    newResult.setImageNumber(i);
//
//                    testResults.add(newResult);
//                }
//            }

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                    final RectF location = result.getLocation();
                    if (location != null && result.getConfidence() >= minimumConfidence) {

//                    if(detectorSettings.getTesting()){
                        OffloadingClassifierResult newResult = new OffloadingClassifierResult();
                        newResult.setLabel(result.getTitle());
                        newResult.setConfidence(result.getConfidence());

                        // map back to original resolution
                        final RectF trackedPos = result.getLocation();
                        frameToCanvasMatrix.mapRect(trackedPos);

                        OffloadingClassifierResult.Coordinate bottomRight = new OffloadingClassifierResult.Coordinate();
//                    bottomRight.setX(result.getLocation().right);
//                    bottomRight.setY(result.getLocation().bottom);
                        bottomRight.setX(trackedPos.right);
                        bottomRight.setY(trackedPos.bottom);
                        newResult.setBottomRight(bottomRight);


                        OffloadingClassifierResult.Coordinate topLeft = new OffloadingClassifierResult.Coordinate();
//                    topLeft.setX(result.getLocation().left);
//                    topLeft.setY(result.getLocation().top);
                        topLeft.setX(trackedPos.left);
                        topLeft.setY(trackedPos.top);
                        newResult.setTopleft(topLeft);

                        newResult.setImageNumber(i);

                        testResults.add(newResult);
//                    }
//                    canvas.drawRect(location, paint);

//                    cropToFrameTransform.mapRect(location);
                        result.setLocation(location);
                        mappedRecognitions.add(result);

                    }
                }

//                int[] rgb2 = new int[w * h];
//                byte[] yuv2 = new byte[w * h];
//
//                croppedBitmapDelay.getPixels(rgb2, 0, w, 0, 0, w, h);
//                populateYUVLuminanceFromRGB(rgb2, yuv2, w, h);
//
//                tracker.trackResults(mappedRecognitions, yuv2, (currTimestamp - detectionDelay));

                previousFrame = croppedBitmapDelay;
//
                try{
                    ObjectMapper mapper = new ObjectMapper();
                    String json_convert = mapper.writeValueAsString(testResults);
                    writeToFile(json_convert);
                }
                catch(Exception e){
                    System.out.println("here");
                }

//            try{
//                int resp = doc.postResult(testResults, i);
//            }
//            catch (Exception e){
//                LOGGER.i(e.getMessage());
//            }

//            if(i == 629){
//                i = 0;
//            }
            }
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

    private void createFileOnDevice(Boolean append) throws IOException {
                /*
                 * Function to initially create the log file and it also writes the time of creation to file.
                 */
        File Root = Environment.getExternalStorageDirectory();
        if(Root.canWrite()){
            File  LogFile = new File(Root, "DetectTestLog.txt");
            FileWriter LogWriter = new FileWriter(LogFile, append);
            outTest = new BufferedWriter(LogWriter);
//      Date currentTime = Calendar.getInstance().getTime();
            Date date = new Date();
            outTest.write("Logged at" + String.valueOf(date.getHours() + ":" + date.getMinutes() + ":" + date.getSeconds() + "\n"));
            outTest.close();

        }
    }

    private void writeToFile(String message) throws IOException {
                /*
                 * Function to initially create the log file and it also writes the time of creation to file.
                 */
        File Root = Environment.getExternalStorageDirectory();
        if(Root.canWrite()){
            File  LogFile = new File(Root, "DetectTestLog.txt");
            FileWriter LogWriter = new FileWriter(LogFile, true);
            outTest = new BufferedWriter(LogWriter);
//      Date currentTime = Calendar.getInstance().getTime();
            Date date = new Date();
            outTest.write(message + "\n");
            outTest.close();

        }
    }

}
