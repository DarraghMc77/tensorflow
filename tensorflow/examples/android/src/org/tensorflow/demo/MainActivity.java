package org.tensorflow.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    Button launchApplication;
    Button settingsButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        launchApplication = (Button) findViewById(R.id.launch_button);

        launchApplication.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this,
                        DetectorActivity.class);

                startActivity(myIntent);
            }
        });

        settingsButton = (Button) findViewById(R.id.settings_button);

        settingsButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent myIntent = new Intent(MainActivity.this,
                        SettingsActivity.class);
                startActivity(myIntent);
            }
        });


    }

}
