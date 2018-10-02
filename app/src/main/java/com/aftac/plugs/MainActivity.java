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

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent queueIntent = new Intent(this.getBaseContext(), Queue.class);
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

    public void gotoDebugMenu(View view) {
        Intent intent = new Intent(this, DebugMenuActivity.class);
        startActivity(intent);
    }

    public void gotoConfigure(View view) {
        // onClick action for Configure button in home screen
        Intent intent = new Intent(this, ConfigurationActivity.class);
        startActivity(intent);
    }

    public void gotoDeploy(View view) {
        // onClick action for Deploy button in home screen
        Intent intent = new Intent(this, DeployActivity.class);
        startActivity(intent);
    }
}
