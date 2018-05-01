package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

import org.tensorflow.demo.env.Logger;

import java.util.ArrayList;


/**
 * Created by Darragh on 01/04/2018.
 */

public class ReadVideo {

    private static final Logger LOGGER = new Logger();

    public Bitmap readVideo(Context context, int imageCount) throws Exception {

        ArrayList<Bitmap> gt_images=new ArrayList<Bitmap>();
        Bitmap bitmap = BitmapFactory.decodeFile("/storage/emulated/0/TestImages/Gt_Images/test" + imageCount + ".jpg");

        return bitmap;
    }

    public double getDifferencePercent(Bitmap img1, Bitmap img2) {
        Bitmap greyImg1 = toGrayscale(img1);
        Bitmap greyImg2 = toGrayscale(img2);

        int width = greyImg1.getWidth();
        int height = greyImg1.getHeight();
        int width2 = greyImg2.getWidth();
        int height2 = greyImg2.getHeight();
        if (width != width2 || height != height2) {
            throw new IllegalArgumentException(String.format("Images must have the same dimensions: (%d,%d) vs. (%d,%d)", width, height, width2, height2));
        }

        long diff = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                diff += pixelDiffGrey(greyImg1.getPixel(x, y), greyImg2.getPixel(x, y));
            }
        }
        long maxDiff = 255 * width * height;

        return 100.0 * diff / maxDiff;
    }

    public int pixelDiff(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xff;
        int g1 = (rgb1 >>  8) & 0xff;
        int b1 =  rgb1        & 0xff;
        int r2 = (rgb2 >> 16) & 0xff;
        int g2 = (rgb2 >>  8) & 0xff;
        int b2 =  rgb2        & 0xff;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    public int pixelDiffGrey(int rgb1, int rgb2) {
        int r1 = rgb1 & 0xff;
        int r2 = rgb2 & 0xff;
        return Math.abs(r1 - r2);
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal){
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

}
