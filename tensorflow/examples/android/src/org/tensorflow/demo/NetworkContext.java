package org.tensorflow.demo;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import org.tensorflow.demo.env.Logger;

import java.io.File;

import javax.annotation.Nonnull;

/**
 * Created by Darragh on 24/05/2018.
 */

public class NetworkContext {


    private static final Logger LOGGER = new Logger();

    private int signalStrength;
    private double uploadBw;
    private double downloadBw;
    private double linkSpeed;
    private String networkType;
    Boolean testingBandwidth;
    private DetectionOffloadingClient doc;

    private static class NetworkContextHolder {
        public static final NetworkContext instance = new NetworkContext();
    }

    @Nonnull
    protected static NetworkContext getInstance() {
        return NetworkContextHolder.instance;
    }


    private NetworkContext() {
        doc = new DetectionOffloadingClient();

    }

    public int getSignalStrength() {
        return signalStrength;
    }

    public double getUploadBw() {
        return uploadBw;
    }

    public double getDownloadBw() {
        return downloadBw;
    }


    public double getLinkSpeed() {
        return linkSpeed;
    }

    public String getNetworkType() {
        return networkType;
    }

    protected synchronized void UpdateNetworkContext(Context context) {

        networkType = getNetworkClass(context);

        updateNetworkLinkSpeed(context);

        updateNetworkSignalLevel(context);

        if (!testingBandwidth) {
            File outputDir = context.getCacheDir();

            try {
                File outputFile = File.createTempFile("testImage",
                        ".jpg", outputDir);
                new TestBandwidth().execute(outputFile.getAbsolutePath());

            } catch (Exception e) {
                LOGGER.i("ERROR", e.toString());
            }
        }

    }

    private class TestBandwidth extends AsyncTask<String, Void, Void> {
        @Override
        protected void onPreExecute() {
            testingBandwidth = true;
        }

        @Override
        protected Void doInBackground(String... params) {
            try {
                long startTime = System.currentTimeMillis();
                doc.testDownloadBw(params[0]);
                long endTime = System.currentTimeMillis();
                File testImage = new File(params[0]);
                downloadBw = (testImage.length() / 1024.0) / ((endTime - startTime) * 1000.0);

                startTime = System.currentTimeMillis();
                byte[] uploadImage = doc.getBytesFromBitmap( BitmapFactory.decodeFile(params[0]));

                doc.testUploadBw(uploadImage);
                endTime = System.currentTimeMillis();
                uploadBw = (uploadImage.length / 1024.0) / ((endTime - startTime) * 1000.0);

                testImage.delete();

            } catch (Exception e) {
                LOGGER.i("ERROR", e.toString());
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            testingBandwidth = false;
        }
    }


    public static String getNetworkClass(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = cm.getActiveNetworkInfo();
        if(info==null || !info.isConnected())
            return "-"; //not connected
        if(info.getType() == ConnectivityManager.TYPE_WIFI)
            return "WIFI";
        if(info.getType() == ConnectivityManager.TYPE_MOBILE){
            int networkType = info.getSubtype();
            switch (networkType) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN: //api<8 : replace by 11
                    return "2G";
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B: //api<9 : replace by 14
                case TelephonyManager.NETWORK_TYPE_EHRPD:  //api<11 : replace by 12
                case TelephonyManager.NETWORK_TYPE_HSPAP:  //api<13 : replace by 15
                    return "3G";
                case TelephonyManager.NETWORK_TYPE_LTE:    //api<11 : replace by 13
                case 19:  //LTE_CA
                    return "4G";
                default:
                    return "?";
            }
        }
        return "?";
    }

    public void updateNetworkLinkSpeed(Context context) {
        WifiManager wifiManager = (WifiManager) context
                .getSystemService(Context.WIFI_SERVICE);
        linkSpeed = wifiManager.getConnectionInfo().getLinkSpeed() / 8 * 1024;
    }

    public void updateNetworkSignalLevel(Context context) {
        TelephonyManager telephonyManager = (TelephonyManager) context
                .getSystemService(Context.TELEPHONY_SERVICE);

        telephonyManager.listen(new PhoneStateListener() {
            @Override
            public void onSignalStrengthsChanged(
                    SignalStrength signalStrength_) {
                super.onSignalStrengthsChanged(signalStrength_);
                signalStrength = signalStrength_.getGsmSignalStrength();
            }
        }, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

    }




}
