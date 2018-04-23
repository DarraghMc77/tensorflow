package org.tensorflow.demo;

/**
 * Created by Darragh on 16/04/2018.
 */

public class SimpleOffloadingDecision implements OffloadingDecisionEngine {

    public DetectorActivity.OffloadingMode makeDecision(long previousDecisionTime, long localProcessingTime){
        if (localProcessingTime < previousDecisionTime){
            return DetectorActivity.OffloadingMode.LOCAL;
        }
        else{
            return DetectorActivity.OffloadingMode.HTTP;
        }

    }
}
