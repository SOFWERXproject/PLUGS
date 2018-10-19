package com.aftac.plugs.DebugActivities;

import android.hardware.Sensor;
import android.os.Bundle;
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

public class TriggerDebugActivity extends AppCompatActivity implements PlugsTrigger.TriggerListener,
        PlugsSensorManager.PlugsSensorEventListener {

    TextView txtView;
    TextView triggerTxt;

    AlphaAnimation fadeIn  = new AlphaAnimation(0.0f, 1.0f);
    AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);

    MagnitudeTrigger trigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        txtView = findViewById(R.id.triggerDebugTxt);
        triggerTxt = findViewById(R.id.triggerDebugTriggerTxt);
        triggerTxt.setAlpha(0.0f);

        init();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        trigger.disable();
    }


    private void init() {
        MagnitudeTrigger trigger = new MagnitudeTrigger(Sensor.TYPE_ACCELEROMETER);
        trigger.setSensitivity(10);
        trigger.enable();

        // This is just so the trigger status / sensor value can be displayed on the debug screen
        trigger.addTriggerListener(this);
        PlugsSensorManager.addSensorEventListener(Sensor.TYPE_ACCELEROMETER, this);
    }

    @Override
    public void onTrigger(QueueTrigger trigger) {
        triggerTxt.startAnimation(fadeIn);
        fadeIn.setDuration(250);
        fadeIn.setFillAfter(true);
        triggerTxt.startAnimation(fadeOut);
        fadeOut.setDuration(1000);
        fadeOut.setFillAfter(true);
        fadeOut.setStartOffset(3000 + fadeIn.getStartOffset() + fadeIn.getDuration());
    }

    @Override
    public void onPlugsSensorEvent(PlugsSensorEvent event) {
        float[] values = event.values;
        float magnitude = (float)Math.sqrt(values[0] * values[0]
                                         + values[1] * values[1]
                                         + values[2] * values[2]);
        txtView.setText("Accelerometer reading: " + (Math.round(magnitude / 100) * 100));
    }
}
