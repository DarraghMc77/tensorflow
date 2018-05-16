package org.tensorflow.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.tensorflow.demo.env.Logger;

public class MainActivity extends Activity {

    Button localButton;
    Button serverButton;
    Button enableTestButton;
    Button disableTestButton;
    Button launchApplication;
    Button enableTrackingButton;
    Button disableTrackingButton;
    Button mButton;
    EditText mEdit;
    DetectorSettings detectorSettings;

    private static final Logger LOGGER = new Logger();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        detectorSettings = new DetectorSettings(DetectorActivity.OffloadingMode.LOCAL, false, true, 416);

        mButton = (Button)findViewById(R.id.text_button);
        mEdit   = (EditText)findViewById(R.id.res_text);

        mButton.setOnClickListener(
                new View.OnClickListener()
                {
                    public void onClick(View view)
                    {
                        LOGGER.i(mEdit.getText().toString());
                        String textBox = mEdit.getText().toString();
                        detectorSettings.setResolution(Integer.parseInt(textBox));
                    }
                });

        localButton = (Button) findViewById(R.id.local_button);

        localButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setOffloadingMode(DetectorActivity.OffloadingMode.LOCAL);
            }
        });

        serverButton = (Button) findViewById(R.id.server_button);

        serverButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setOffloadingMode(DetectorActivity.OffloadingMode.HTTP);
            }
        });

        disableTestButton = (Button) findViewById(R.id.disable_testing_button);

        disableTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setTesting(false);
            }
        });

        enableTestButton = (Button) findViewById(R.id.enable_testing_button);

        enableTestButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setTesting(true);

            }
        });

        enableTrackingButton = (Button) findViewById(R.id.enable_tracking_button);

        enableTrackingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setEnableTracking(true);
            }
        });

        disableTrackingButton = (Button) findViewById(R.id.disable_tracking_button);

        disableTrackingButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                detectorSettings.setEnableTracking(false);

            }
        });

        launchApplication = (Button) findViewById(R.id.launch_button);

        launchApplication.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this,
                        DetectorActivity.class);

                myIntent.putExtra("detectorSettings", detectorSettings);
                startActivity(myIntent);
            }
        });

    }

}
