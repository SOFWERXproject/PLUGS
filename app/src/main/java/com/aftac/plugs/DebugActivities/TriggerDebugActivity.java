package com.aftac.plugs.DebugActivities;

import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.animation.AlphaAnimation;
import android.widget.TextView;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueCommand;
import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.R;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;
import com.aftac.plugs.Triggers.MagnitudeTrigger;
import com.aftac.plugs.Triggers.PlugsTrigger;

import org.json.JSONArray;

import java.util.Arrays;

public class TriggerDebugActivity extends AppCompatActivity implements
        PlugsSensorManager.PlugsSensorEventListener {

    TextView txtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_trigger);

        txtView = findViewById(R.id.triggerDebugTxt);

        init();
    }

    private void init() {
        Handler mainHandler = new Handler(Looper.getMainLooper());

        PlugsSensorManager.addSensorEventListener(Sensor.TYPE_ACCELEROMETER, this,
                mainHandler);
    }

    @Override
    public void onPlugsSensorEvent(PlugsSensorEvent event) {
        float[] values = event.values;

        float magnitude = (float)Math.sqrt(values[0] * values[0]
                                         + values[1] * values[1]
                                         + values[2] * values[2]);
        txtView.setText("Accelerometer reading: " + (Math.round(magnitude * 100) / 100.0f));
    }
}
