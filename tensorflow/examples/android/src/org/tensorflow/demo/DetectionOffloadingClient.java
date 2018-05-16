package org.tensorflow.demo;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.telephony.TelephonyManager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.tensorflow.demo.env.Logger;

import java.io.ByteArrayOutputStream;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by Darragh on 09/02/2018.
 */

//TODO: losing 15ms somewhere in here, taking 5ms over network, taking 64ms on server for detection, around 90ms following all of this
// Could save about 3 ms using sockets as opposed to http
public class DetectionOffloadingClient {

    ObjectMapper mapper = new ObjectMapper();
    private static final Logger LOGGER = new Logger();

    private static final int SERVER_TIMEOUT = 500;

    private static final MediaType MEDIA_TYPE_PLAINTEXT = MediaType
            .parse("text/plain; charset=uint-8");

    private final OkHttpClient client = new OkHttpClient();

//    OkHttpClient client1 = client.newBuilder()
//            .connectTimeout(500, TimeUnit.MILLISECONDS)
//            .writeTimeout(500, TimeUnit.MILLISECONDS)
//            .readTimeout(500, TimeUnit.MILLISECONDS)
//            .build();

    OkHttpClient client1 = client.newBuilder()
            .build();

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    public List<OffloadingClassifierResult> postImage(Bitmap image, final long startTime) throws Exception {
        long startTime2 = SystemClock.uptimeMillis();
        byte[] byteImage = getBytesFromBitmap(image);
        LOGGER.i("Converted to bytes: " + (SystemClock.uptimeMillis() - startTime2));

        Request request = new Request.Builder()
                .url("http://192.168.6.131:5010/detect")
                .post(RequestBody.create(MEDIA_TYPE_PLAINTEXT, byteImage))
                .build();

        startTime2 = SystemClock.uptimeMillis();


        Response response = client1.newCall(request).execute();
//        System.out.println("Response 1 succeeded: " + response);
        LOGGER.i("Response 1 succeeded: " + response);

        LOGGER.i("Sent and Received Result: " + (SystemClock.uptimeMillis() - startTime2));
        String restr = response.body().string();

        startTime2 = SystemClock.uptimeMillis();
        List<OffloadingClassifierResult> classifierResult = mapper.readValue(restr, new TypeReference<List<OffloadingClassifierResult>>(){});
        LOGGER.i("Mapped to new value: " + (SystemClock.uptimeMillis() - startTime2));
        return classifierResult;

//        LOGGER.i("Sent and Received Result: " + (SystemClock.uptimeMillis() - startTime2));
//        String restr = response.body().string();
//
//        startTime2 = SystemClock.uptimeMillis();
//        List<OffloadingClassifierResult> classifierResult = mapper.readValue(restr, new TypeReference<List<OffloadingClassifierResult>>(){});
//        LOGGER.i("Mapped to new value: " + (SystemClock.uptimeMillis() - startTime2));

//        return classifierResult;
    }

//    protected String doInBackground testBandwidth(){
//
//        Request request = new Request.Builder()
//                .url("http://192.168.6.131:5010/test_download")
//                .build();
//
//        return "";
//    }

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

    public int postResult(List<OffloadingClassifierResult> results, int image_number) throws Exception {
        String json_convert = mapper.writeValueAsString(results);

        Request request = new Request.Builder()
                .url("http://192.168.6.131:5010/result")
                .post(RequestBody.create(JSON, json_convert))
                .build();

        Response response = client.newCall(request).execute();
        return 1;
    }

    public byte[] getBytesFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, stream);
        return stream.toByteArray();
    }
}
