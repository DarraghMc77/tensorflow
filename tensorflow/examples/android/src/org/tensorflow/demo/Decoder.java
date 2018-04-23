package org.tensorflow.demo;

import android.os.AsyncTask;
import android.util.Log;

import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.FileChannelWrapper;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;

import java.io.File;


public class Decoder extends AsyncTask<File, Integer, Integer> {
    private static final String TAG = "DECODER";

    protected Integer doInBackground(File... params) {
        try {
            File file = new File("/storage.emulated.0.DCIM/Camera/VID_20180321_180633.mp4");
            FrameGrab grab = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file));
            Picture picture;
            while (null != (picture = grab.getNativeFrame())) {
                System.out.println(picture.getWidth() + "x" + picture.getHeight() + " " + picture.getColor());
            }

            FileChannelWrapper ch = null;
        } catch (Exception e) {
            Log.e(TAG, "IO", e);}
//        try {
//            ch = NIOUtils.readableFileChannel(params[0]);
//            FrameGrab frameGrab = new FrameGrab(ch);
//            MediaInfo mi = frameGrab.getMediaInfo();
//            Bitmap frame = Bitmap.createBitmap(mi.getDim().getWidth(), mi.getDim().getHeight(), Bitmap.Config.ARGB_8888);
//
//            for (int i = 0; !flag; i++) {
//
//                frameGrab.getFrame(frame);
//                if (frame == null)
//                    break;
//                OutputStream os = null;
//                try {
//                    os = new BufferedOutputStream(new FileOutputStream(new File(params[0].getParentFile(), String.format("img%08d.jpg", i))));
//                    frame.compress(CompressFormat.JPEG, 90, os);
//                } finally {
//                    if (os != null)
//                        os.close();
//                }
//                publishProgress(i);
//
//            }
//        } catch (IOException e) {
//            Log.e(TAG, "IO", e);
//        } catch (JCodecException e) {
//            Log.e(TAG, "JCodec", e);
//        } finally {
//            NIOUtils.closeQuietly(ch);
//        }
//        return 0;
            return 0;
    }

//    @Override
//    protected void onProgressUpdate(Integer... values) {
//        progress.setText(String.valueOf(values[0]));
//    }
}
