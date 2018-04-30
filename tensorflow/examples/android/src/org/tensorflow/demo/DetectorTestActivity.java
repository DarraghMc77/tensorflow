package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.os.StrictMode;
import android.os.SystemClock;

import org.tensorflow.demo.env.ImageUtils;
import org.tensorflow.demo.env.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Darragh on 16/04/2018.
 */

public class DetectorTestActivity {

    private ReadVideo videoReader = new ReadVideo();
    int numberOfImages = 629;
    private static final Logger LOGGER = new Logger();
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;
    float minimumConfidence = 0.6f;
    private DetectionOffloadingClient doc = new DetectionOffloadingClient();
    private Bitmap previousFrame = null;

    private static final boolean MAINTAIN_ASPECT = true;

    int cropSize = 300;

    public void testImages(Context context, Classifier detector){

        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);

        int i;
        for(i=0; i < numberOfImages; i++){
            rgbFrameBitmap = Bitmap.createBitmap(300, 300, Config.ARGB_8888);
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            300, 300,
                            cropSize, cropSize,
                            0, MAINTAIN_ASPECT);

            ArrayList<Bitmap> gt_images = new ArrayList<Bitmap>();
            try {
                gt_images.add(videoReader.readVideo(context, i));
            }
            catch (Exception e){
                System.out.println("err");
                LOGGER.i("NEW ERROR: " + e.getMessage());
            }

            final Canvas canvas = new Canvas(croppedBitmap);
            canvas.drawBitmap(gt_images.get(0), frameToCropTransform, null);

            if(i > 2) {
                double frameDifference = videoReader.getDifferencePercent(croppedBitmap, previousFrame);
                LOGGER.i("FRAME DIFFERENCE: image: " + i + "difference: "+ frameDifference);
            }

//            List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

            final long startTime = SystemClock.uptimeMillis();
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

//            List<OffloadingClassifierResult> testResults = new ArrayList<OffloadingClassifierResult>();;

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

            if(i == numberOfImages - 2){
                i=0;
            }

            previousFrame = croppedBitmap;

//            try{
//                int resp = doc.postResult(testResults, i);
//            }
//            catch (Exception e){
//                LOGGER.i(e.getMessage());
//            }
        }

    }
}
