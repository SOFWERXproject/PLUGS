package com.aftac.plugs;

import android.content.Intent;
import android.hardware.Sensor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Sensors.PlugsSensors;
import android.view.View;

import org.json.JSONArray;

import java.util.Arrays;
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
                Queue.COMMAND_TARGET_SELF,  // The target device in the mesh network
                Queue.COMMAND_CLASS_MISC,   // Owner "class" of the command
                Queue.COMMAND_MISC_STOP,    // The command id
                null);
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

    @Override
    public void onQueueStarted() {
        // An example of a command sent to the Queue, and receiving a response

        // Create an array for the command's arguments
        Object[] args = { Sensor.TYPE_ALL, "Test message" };

        // Build the command
        Queue.Command command = new Queue.Command(
                Queue.COMMAND_TARGET_SELF,          // The target device in the mesh network
                Queue.COMMAND_CLASS_SENSORS,        // Owner "class" of the command
                PlugsSensors.COMMAND_GET_SENSORS,   // The command id
                new JSONArray(Arrays.asList(args)));

        // Set a listener for the command's response
        command.setResponseListener((response, cmd) -> {
            JSONArray sensorList = (JSONArray)response;
            try { Log.v("PLUGS", sensorList.toString(1)); }
            catch (Exception e) { Log.e("PLUGS", "Exception", e); }
            Log.v("PLUGS", "Number of sensors: " + sensorList.length());
        });

        // Push the command out to the queue
        Queue.push(command);
    }
}
