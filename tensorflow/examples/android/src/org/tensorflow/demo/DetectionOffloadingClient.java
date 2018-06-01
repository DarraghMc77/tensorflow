package org.tensorflow.demo;

import android.graphics.Bitmap;
import android.os.SystemClock;

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

    public List<OffloadingClassifierResult> postImage(Bitmap image, final double bandwidth) throws Exception {
        long startTime2 = SystemClock.uptimeMillis();
        byte[] byteImage = getBytesFromBitmap(image);
        long leng = byteImage.length;
        LOGGER.i("Converted to bytes: " + (SystemClock.uptimeMillis() - startTime2));

        Request request = new Request.Builder()
                .url("http://192.168.6.131:5010/detect")
                .post(RequestBody.create(MEDIA_TYPE_PLAINTEXT, byteImage))
                .build();

        startTime2 = SystemClock.uptimeMillis();

        // simulate bandwidth
//        int sizeInBytes = byteImage.length;
//        double arrivalTimesMs = System.currentTimeMillis() + (sizeInBytes*8.0 / (1024*1024*bandwidth))*1000;
//        double tArrivalTimesMs = (sizeInBytes*8.0 / (1024*1024*bandwidth))*1000;

//        Thread.sleep((long)tArrivalTimesMs);
//        while( System.currentTimeMillis() < arrivalTimesMs);

        Response response = client1.newCall(request).execute();
        LOGGER.i("Response 1 succeeded: " + response);

        LOGGER.i("Sent and Received Result: " + (SystemClock.uptimeMillis() - startTime2));
        String restr = response.body().string();

        startTime2 = SystemClock.uptimeMillis();
        List<OffloadingClassifierResult> classifierResult = mapper.readValue(restr, new TypeReference<List<OffloadingClassifierResult>>(){});
        LOGGER.i("Mapped to new value: " + (SystemClock.uptimeMillis() - startTime2));
        return classifierResult;

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

    public int testDownloadBw(String fileLocation) throws Exception {

        Request request = new Request.Builder()
                .url("http://192.168.6.131:5010/test_download")
                .build();

        Response response = client.newCall(request).execute();

        return response.code();
    }

    public int testUploadBw(byte[] uploadImage) throws Exception {

        Request request = new Request.Builder()
                .url("http://192.168.6.131:5010/test_upload")
                .post(RequestBody.create(MEDIA_TYPE_PLAINTEXT, uploadImage))
                .build();

        Response response = client.newCall(request).execute();
        return response.code();
    }

    public byte[] getBytesFromBitmap(Bitmap bitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
        return stream.toByteArray();
    }
}
