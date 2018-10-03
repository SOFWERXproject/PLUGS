package com.aftac.plugs;


import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;


import com.aftac.plugs.DebugActivities.DebugMenuActivity;

public class HomeStationActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_station);
    }

    public void scanForHomeStation(View view) {
        //Scan for home station
        Log.d("BUTTON", "Scan for home station button pressed");
    }
    public void connectToHomeStation(View view) {
        //Connect to home station
        Log.d("BUTTON", "Connect to home station button pressed");
    }
}
