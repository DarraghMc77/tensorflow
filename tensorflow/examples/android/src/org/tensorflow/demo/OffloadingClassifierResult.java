package org.tensorflow.demo;

/**
 * Created by Darragh on 09/02/2018.
 */

public class OffloadingClassifierResult {

    Coordinate topleft;
    Coordinate bottomright;
    float confidence;
    String label;
    int imageNumber;

    public int getImageNumber() {
        return imageNumber;
    }

    public void setImageNumber(int imageNumber) {
        this.imageNumber = imageNumber;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public float getConfidence() {

        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public Coordinate getBottomRight() {

        return bottomright;
    }

    public void setBottomRight(Coordinate bottomright) {
        this.bottomright = bottomright;
    }

    public Coordinate getTopleft() {

        return topleft;
    }

    public void setTopleft(Coordinate topleft) {
        this.topleft = topleft;
    }

    public static class Coordinate{
        float x;
        float y;

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }
    }

}
