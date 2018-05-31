package org.tensorflow.demo;

/**
 * Created by Darragh on 16/04/2018.
 */

public class SimpleOffloadingDecision implements OffloadingDecisionEngine {

    public DetectorActivity.OffloadingMode makeDecision(NetworkContext networkContext){
        if(networkContext.getNetworkType() == "-"){
            return DetectorActivity.OffloadingMode.LOCAL;
        }
        if(networkContext.getNetworkType() == "WIFI" && networkContext.getUploadBw() > 1){
            return DetectorActivity.OffloadingMode.HTTP;
        }
        else{
            return DetectorActivity.OffloadingMode.HTTP;
        }
    }

    public int getResolution(NetworkContext networkContext, int minSpeed){
        if(networkContext.getUploadBw() < 1){
            return 96;
        }
        else if(networkContext.getUploadBw() > 1 & networkContext.getUploadBw() < 2){
            return 160;
        }
        else if(networkContext.getUploadBw() > 1 & networkContext.getUploadBw() < 2){
            return 160;
        }
        else if(networkContext.getUploadBw() > 2 & networkContext.getUploadBw() < 3){
            return 288;
        }
        else if(networkContext.getUploadBw() > 3 & networkContext.getUploadBw() < 4){
            return 352;
        }
        else{
            return 416;
        }
    }
}
