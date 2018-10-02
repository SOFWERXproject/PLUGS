package com.aftac.plugs;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.aftac.plugs.Queue.Queue;
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

        test_queue();
    }


    public void test_queue() {
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
            try {
                Log.v("PLUGS", sensorList.toString(1));
                //txtView.setText(sensorList.toString(4));
            }
            catch (Exception e) { Log.e("PLUGS", "Exception", e); }
            Log.v("PLUGS", "Number of sensors: " + sensorList.length());
        });

        // Push the command out to the queue
        Queue.push(command);
    }
}
