package com.aftac.plugs.DebugActivities;

import android.hardware.Sensor;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueCommand;
import com.aftac.plugs.R;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import org.json.JSONArray;

import java.util.Arrays;

public class QueueDebugActivity extends AppCompatActivity {

    TextView txtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_queue);

        txtView = findViewById(R.id.queueDebugText);

        test_queue();
    }

    public void test_queue() {
        // An example of a command sent to the Queue, and receiving a response

        // Create an array for the command's arguments
        Object[] args = { Sensor.TYPE_ALL, "Test message" };

        // Build the command
        QueueCommand command = new QueueCommand(
                Queue.COMMAND_TARGET_SELF,          // The target device in the mesh network
                Queue.COMMAND_CLASS_SENSORS,        // Owner "class" of the command
                PlugsSensorManager.COMMAND_GET_SENSORS,   // The command id
                new JSONArray(Arrays.asList(args)));

        // Set a listener for the command's response
        command.setResponseListener((response, cmd) -> {
            JSONArray sensorList = (JSONArray)response;
            try {
                Log.v("PLUGS", sensorList.toString(1));
                txtView.setText(sensorList.toString(4));
            }
            catch (Exception e) { Log.e("PLUGS", "Exception", e); }
            Log.v("PLUGS", "Number of sensors: " + sensorList.length());
        });

        // Push the command out to the queue
        Queue.push(command);
    }
}
