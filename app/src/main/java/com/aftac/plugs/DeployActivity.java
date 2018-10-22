package com.aftac.plugs;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

public class DeployActivity extends AppCompatActivity {

    private static SharedPreferences prefs;
    String counterSetting;
    TextView timerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deploy);
        //counterSetting = "30";
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //prefs.registerOnSharedPreferenceChangeListener(prefsListener);
        counterSetting = prefs.getString("general_deploy_timer", "30");
        timerText = (TextView) findViewById(R.id.textViewCounter);
        timerText.setText(counterSetting);
    }

    //Setup the countdown timer for the deployment
    CountDownTimer cTimer = null;

    //Start the timer
    void startTimer() {
        cTimer = new CountDownTimer(Integer.parseInt(counterSetting) * 1000, 1000) {
            public void onTick(long millisUntilFinished) {
                timerText.setText(Long.toString(millisUntilFinished / 1000));
            }
            public void onFinish() {
                // Activate all deployed functions
                timerText.setText("DEPLOYED");
            }
        };
        cTimer.start();
    }

    //Cancel the timer
    void cancelTimer(){
        if(cTimer != null) {
            timerText.setText(counterSetting);
            cTimer.cancel();
        }
    }

    // Button actions
    public void clickedDeploy(View view) {
        startTimer();
    }

    public void clickCancel(View view) {
        cancelTimer();
    }
}
