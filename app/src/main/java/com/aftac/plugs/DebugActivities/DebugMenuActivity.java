package com.aftac.plugs.DebugActivities;

import android.content.Intent;
import android.hardware.Sensor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.aftac.plugs.R;
import com.aftac.plugs.Sensors.PlugsSensorManager;
import com.aftac.plugs.Triggers.MagnitudeTrigger;

public class DebugMenuActivity extends AppCompatActivity{
    private static final int MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 42;
    private TextView txtView;

    MagnitudeTrigger trigger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_menu);

        initTrigger();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        trigger.disable();
    }

    public void gotoDebugMesh(View view) {
        Intent intent = new Intent(this, MeshDebugActivity.class);
        startActivity(intent);
    }

    public void gotoDebugQueue(View view) {
        Intent intent = new Intent(this, QueueDebugActivity.class);
        startActivity(intent);
    }

    public void gotoDebugSensors(View view) {
        //Intent intent = new Intent(this, SensorDebugActivity.class);
        //startActivity(intent);
    }

    public void gotoDebugTriggers(View view) {
        Intent intent = new Intent(this, TriggerDebugActivity.class);
        startActivity(intent);
    }

    void initTrigger() {
        trigger = new MagnitudeTrigger(Sensor.TYPE_ACCELEROMETER);
        trigger.setSensitivity(15);
        trigger.enable();
    }

}
