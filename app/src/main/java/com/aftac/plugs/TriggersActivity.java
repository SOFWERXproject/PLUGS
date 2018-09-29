package com.aftac.plugs;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;

public class TriggersActivity extends AppCompatActivity {

    private SeekBar accelBar, micBar;
    private TextView micET, accelET;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_triggers);
        initializeVariables();
        // Create the listeners for the bars
        accelBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean b) {
               accelET.setText(Integer.toString(progressValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        micBar.setOnSeekBarChangeListener((new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressValue, boolean b) {
                micET.setText(Integer.toString(progressValue));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        }));
    }

    private void initializeVariables() {
        accelBar = findViewById(R.id.seekBarAccelerometer);
        micBar = findViewById(R.id.seekBarMicrophone);
        accelET = findViewById(R.id.editTextAccelerometer);
        micET = findViewById(R.id.editTextMicrophone);
    }

    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        // Check which checkbox was clicked
        switch(view.getId()) {
            // Add more sensor triggers as needed
            case R.id.checkBoxAccelerometer:
                if (checked) {
                    // Make the SeekBar visible
                    accelBar.setVisibility(View.VISIBLE);
                    accelET.setVisibility(View.VISIBLE);
                }else {
                    // Make it gone
                    accelBar.setVisibility(View.GONE);
                    accelET.setVisibility(View.GONE);
                }break;
            case R.id.checkBoxMicrophone:
                if (checked) {
                    // Make the SeekBar visible
                    micBar.setVisibility(View.VISIBLE);
                    micET.setVisibility(View.VISIBLE);
                }else {
                    // Make it gone
                    micBar.setVisibility(View.GONE);
                    micET.setVisibility(View.GONE);
                }break;
        }
    }
}
