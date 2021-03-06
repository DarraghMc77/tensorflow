package org.tensorflow.demo;

/**
 * Created by Darragh on 16/04/2018.
 */

public interface OffloadingDecisionEngine {

    public DetectorActivity.OffloadingMode makeDecision(NetworkContext networkContext);

    public int getResolution(NetworkContext networkContext, int minSpeed);

}
