package com.aftac.plugs;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.aftac.Plugs;
import com.aftac.plugs.DebugActivities.DebugMenuActivity;
import com.aftac.plugs.Queue.Queue;

import android.view.View;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent queueIntent = new Intent(this.getBaseContext(), Queue.class);
        checkPermissions();
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

    private void checkPermissions() {
        int permissionsMissing = 0;
        int i = Plugs.permissionsNeeded.length,
                count = 0;
        while (--i >= 0) {
            permissionsMissing <<= 1;
            if (ContextCompat.checkSelfPermission(this, Plugs.permissionsNeeded[i]) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionsMissing |= 1;
                count++;
            }
        }

        if (permissionsMissing > 0) {
            String[] permissions = new String[count];
            i = 0;
            count = 0;
            while (permissionsMissing > 0) {
                if ((permissionsMissing & 1) == 1)
                    permissions[count++] = Plugs.permissionsNeeded[i];
                i++;
                permissionsMissing >>= 1;
            }

            ActivityCompat.requestPermissions(this, permissions, 1);
        }
    }
}
