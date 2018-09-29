package com.aftac.plugs;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class ConfigurationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_configuration);
    }
    public void gotoTriggers(View view) {
        // onClick action for Triggers button in Configuration screen
        Intent intent = new Intent(this, TriggersActivity.class);
        startActivity(intent);
    }

    public void gotoActions(View view) {
        // onClick action for Actions button in Configuration screen
        Intent intent = new Intent(this, ActionsActivity.class);
        startActivity(intent);
    }
    public void gotoHomeStation(View view) {
        // onClick action for HomeStation button in Configuration screen
        Intent intent = new Intent(this, HomeStationActivity.class);
        startActivity(intent);
    }

    public void gotoGeneral(View view) {
        // onClick action for General button in Configuration screen
        Intent intent = new Intent(this, GeneralActivity.class);
        startActivity(intent);
    }
}
