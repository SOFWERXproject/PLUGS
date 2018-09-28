package com.aftac.plugs;

import android.content.Intent;
import android.hardware.Sensor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Sensors.PlugsSensors;
import android.view.View;

import java.util.List;

import static android.hardware.Sensor.TYPE_ALL;

public class MainActivity extends AppCompatActivity implements Queue.onStartedListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent queueIntent = new Intent(this.getBaseContext(), Queue.class);

        Queue.setStartedListener(this);
        startService(queueIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Queue.Command command = new Queue.Command(
                Queue.COMMAND_TARGET_NONE,
                Queue.COMMAND_CLASS_MISC,
                Queue.COMMAND_MISC_STOP,
                null);
        Queue.push(command);
    }

    @Override
    public void onQueueStarted() {
        // An example of a command sent to the Queue, and receiving a response
        Queue.Command command = new Queue.Command(
                Queue.COMMAND_TARGET_NONE,          // The target device in the mesh network
                Queue.COMMAND_CLASS_SENSORS,        // "class" of the command
                PlugsSensors.COMMAND_GET_SENSORS,   // The command id
                new Object[] {
                        // Arguments
                    Sensor.TYPE_ALL,
                    "This is a test message"
                });

        //command.setResponseListener((List<Sensor> sensorList) -> {
        //   Log.v("PLUGS", "Number of sensors: " + sensorList.size());
        //});
        Queue.push(command);
    }

    public void gotoConfigure(View view) {
        // Go to configuration screen
        Intent intent = new Intent(this, ConfigurationActivity.class);
        startActivity(intent);
    }

    public void gotoDeploy(View view) {
        // Deploy phone
        Intent intent = new Intent(this, DeployActivity.class);
        startActivity(intent);
    }
}
