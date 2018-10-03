package com.aftac.plugs.DebugActivities;

import android.content.Intent;
import android.hardware.Sensor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.R;
import com.aftac.plugs.Sensors.PlugsSensors;

import org.json.JSONArray;

import java.util.Arrays;

public class DebugMenuActivity extends AppCompatActivity{
    private static final int MY_PERMISSIONS_ACCESS_COARSE_LOCATION = 42;
    private TextView txtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_menu);
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

}
