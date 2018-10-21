package com.aftac.plugs;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.aftac.Plugs;
import com.aftac.plugs.DebugActivities.DebugMenuActivity;
import com.aftac.plugs.MeshNetwork.MeshManager;
import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueCommand;

import android.view.View;

public class MainActivity extends AppCompatActivity {
    public boolean permissionsChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Perform app initialization
        doInitialization();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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

    private void doInitialization() {
        if (!permissionsChecked)
            checkPermissions();
        else
            startService(new Intent(this, Queue.class));
    }

    private void checkPermissions() {
        long permissionsMissing = 0;
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

        permissionsChecked = true;

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
        } else
            doInitialization();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        doInitialization();
    }
}
