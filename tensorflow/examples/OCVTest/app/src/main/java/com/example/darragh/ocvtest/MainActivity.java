package com.example.darragh.ocvtest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;

public class MainActivity extends AppCompatActivity {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
        System.loadLibrary("opencv_java3");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Example of a call to a native method
        TextView textView = (TextView) findViewById(R.id.sample_text);
        textView.setText(stringFromJNI());

        if(!OpenCVLoader.initDebug()){
            textView.setText(textView.getText() + "\n OpenCv not working");
        }
        else{
            textView.setText(textView.getText() + "\n OpenCv WORKING");
            textView.setText(textView.getText() + "\n" + validate(0L, 0L));
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
    public native String stringFromJNI();

    public native String validate(long matAddrGray, long matAddrRgba);
}
