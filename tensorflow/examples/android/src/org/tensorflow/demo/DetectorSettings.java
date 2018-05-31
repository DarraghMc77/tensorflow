package org.tensorflow.demo;

import java.io.Serializable;

/**
 * Created by Darragh on 23/04/2018.
 */

@SuppressWarnings("serial")
public class DetectorSettings implements Serializable {

    private DetectorActivity.OffloadingMode offloadingMode;
    private Boolean testing;
    private Boolean enableTracking;
    private int resolution;
    private Boolean trackingDecision;

    public DetectorSettings(DetectorActivity.OffloadingMode offloadingMode, Boolean testing, Boolean enableTracking, int resolution, Boolean trackingDecision){
        this.offloadingMode = offloadingMode;
        this.testing = testing;
        this.enableTracking = enableTracking;
        this.resolution = resolution;
        this.trackingDecision = trackingDecision;
    }

    public Boolean getEnableTracking() {
        return enableTracking;
    }

    public void setEnableTracking(Boolean enableTracking) {
        this.enableTracking = enableTracking;
    }

    public DetectorActivity.OffloadingMode getOffloadingMode() {
        return this.offloadingMode;
    }

    public void setOffloadingMode(DetectorActivity.OffloadingMode offloadingMode) {
        this.offloadingMode = offloadingMode;
    }

    public Boolean getTesting() {
        return testing;
    }

    public void setTesting(Boolean testing) {
        this.testing = testing;
    }

    public Boolean getTrackingDecision() {
        return trackingDecision;
    }

    public void setTrackingDecision(Boolean trackingDecision) {
        this.trackingDecision = trackingDecision;
    }

    public int getResolution() {
        return resolution;
    }

    public void setResolution(int resolution) {
        this.resolution = resolution;
    }

}
