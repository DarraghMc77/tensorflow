package org.tensorflow.demo;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends Activity {

    Button localButton;
    Button serverButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        localButton = (Button) findViewById(R.id.local_button);

        localButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                // Start NewActivity.class
                DetectorSettings detectorSettings = new DetectorSettings();
                detectorSettings.setOffloadingMode(DetectorActivity.OffloadingMode.LOCAL);

                Intent myIntent = new Intent(MainActivity.this,
                        DetectorActivity.class);

                myIntent.putExtra("sampleObject", detectorSettings);
                startActivity(myIntent);
            }
        });

        serverButton = (Button) findViewById(R.id.server_button);

        serverButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Code here executes on main thread after user presses button
                // Start NewActivity.class
                DetectorSettings detectorSettings = new DetectorSettings();
                detectorSettings.setOffloadingMode(DetectorActivity.OffloadingMode.HTTP);

                Intent myIntent = new Intent(MainActivity.this,
                        DetectorActivity.class);

                myIntent.putExtra("sampleObject", detectorSettings);
                startActivity(myIntent);
            }
        });

    }

}
