package com.aftac.plugs.DebugActivities;

import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.aftac.lib.file_io_wrapper.BinaryFileOutputStream;
import com.aftac.plugs.Gps.GpsService;
import com.aftac.plugs.R;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.io.File;
import java.nio.ByteBuffer;

public class TriggerDebugActivity extends AppCompatActivity implements
        PlugsSensorManager.PlugsSensorEventListener {

    //BinaryFileOutputStream file;

    TextView txtView;
    float average = 0, avg2 = 0, min = Float.MAX_VALUE, max = Float.MIN_VALUE;
    float avgMax = 0, avgMin = 0;
    int settleSamples = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_trigger);

        txtView = findViewById(R.id.triggerDebugTxt);

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //try { file.close(); } catch (Exception e) { e.printStackTrace(); }
    }

    private void init() {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        /*String filename = Environment.getExternalStorageDirectory() + File.separator + "accelerometer_data.bin";

        try {
            file = new BinaryFileOutputStream(filename, BinaryFileOutputStream.Endianness.BIG_ENDIAN);
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        PlugsSensorManager.addSensorEventListener(Sensor.TYPE_ACCELEROMETER, this,
                mainHandler);
    }

    @Override
    public void onPlugsSensorEvent(PlugsSensorEvent event) {
        float[] values = event.values;

        float magnitude = (float)Math.sqrt(values[0] * values[0]
                                         + values[1] * values[1]
                                         + values[2] * values[2]);
        if (average == 0) {
            average = magnitude;
            return;
        }

        average = (float)(average * 0.9999 + magnitude * 0.0001);
        magnitude = Math.abs(magnitude - average);
        magnitude *= 9.8 / average;
        avg2 = avg2 * 0.9f + magnitude * 0.1f;

        /*try {
            String val = String.valueOf(magnitude) + "\n";
            file.write(val.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }*/

        if (avg2 > max && settleSamples == 0) {
            max = avg2;//magnitude;
            //if (settleSamples > 0) max = magnitude;
            //else max = max * .99f + magnitude * .01f;
        }
        else {
            //if (avgMax == 0) avgMax = max;
            //else avgMax = (float)(avgMax * .99 + max * .01);
            //max *= .999;
        }
        if (magnitude < min) min = magnitude;
        else {
            if (avgMin == 0) avgMin = min;
            else avgMin = (float)(avgMin * .99 + min * .01);
            min *= 1.001;
        }

        //if (magnitude < avgMax)


        if (settleSamples > 0) {
            avgMax = max * .5f + avgMax * .5f;
            avgMin = min * .5f + avgMin * .5f;
            settleSamples--;
        }

        if (avg2 > 0.03f) {
            txtView.setText(GpsService.getFormattedUtcTime("HH:mm:ss.SSS")+ "\n"
                    + "Accelerometer reading: " + (Math.round(magnitude * 1000) / 1000.0f) + "\n"
                    + "Max: " + max + "\n"
                    + "Min: " + avgMin + "\n"
                    + "Average: " + average + ", " + avg2 + "\n"
                    + "TRIGGER");
        } else {
            txtView.setText(GpsService.getFormattedUtcTime("HH:mm:ss.SSS")+ "\n"
                    + "Accelerometer reading: " + (Math.round(magnitude * 1000) / 1000.0f) + "\n"
                    + "Max: " + max + "\n"
                    + "Min: " + avgMin + "\n"
                    + "Average: " + average + ", " + avg2);
        }
    }
}
