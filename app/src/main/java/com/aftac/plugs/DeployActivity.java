package com.aftac.plugs;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.TextView;

public class DeployActivity extends AppCompatActivity {

    int counterSetting;
    TextView timerText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_deploy);

        counterSetting = 30;
        timerText = (TextView) findViewById(R.id.textViewCounter);
    }

    //Setup the countdown timer for the deployment
    CountDownTimer cTimer = null;

    //Start the timer
    void startTimer() {
        cTimer = new CountDownTimer(counterSetting * 1000, 1000) {
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
            timerText.setText(Integer.toString(counterSetting));
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
