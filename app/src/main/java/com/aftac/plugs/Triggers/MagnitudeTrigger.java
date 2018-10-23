package com.aftac.plugs.Triggers;

import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.nio.ByteBuffer;

public class MagnitudeTrigger extends PlugsTrigger implements PlugsSensorManager.PlugsSensorEventListener {
    private static final int SETTLE_SAMPLE_NUM = 100;

    int sensorType;
    int sensorIndex;
    float sensitivity;
    float average = 0;
    float calibValue = 0;

    boolean calibrate = false;
    public float lastValue;
    //float runningAverage = 0;

    int settleCounter = SETTLE_SAMPLE_NUM;
    boolean enabled = false;

    public MagnitudeTrigger(int sensorType, int sensorIndex) {
        this.sensorType = sensorType;
        this.sensorIndex = sensorIndex;
        PlugsSensorManager.addSensorEventListener(sensorType, sensorIndex, this);
    }
    public MagnitudeTrigger(int sensorType) {
        this.sensorType = sensorType;
        this.sensorIndex = -1;
        PlugsSensorManager.addSensorEventListener(sensorType, this);
    }

    public void detach() {
        PlugsSensorManager.removeSensorEventListener(sensorType, sensorIndex,this);
    }

    public boolean isEnabled() { return enabled; }
    public void enable()  { enabled = true;  settleCounter = SETTLE_SAMPLE_NUM; }
    public void disable() { enabled = false; }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
        settleCounter = SETTLE_SAMPLE_NUM;
    }

    public void setCalibration(float calibValue) {
        this.calibValue = calibValue;
        calibrate = true;
    }

    @Override
    public void onPlugsSensorEvent(PlugsSensorEvent event) {
        float[] values = event.values;
        float magnitude = (float) Math.sqrt(values[0] * values[0]
                                          + values[1] * values[1]
                                          + values[2] * values[2]);
        if (average == 0) average = magnitude;
        else average = average * 0.9999f + average * 0.0001f;


        magnitude = Math.abs(magnitude - average);
        if (calibrate)
            magnitude *= calibValue / average;

        magnitude = lastValue * 0.5f + magnitude * 0.5f;

        //runningAverage = (float) ((magnitude * 0.1) + runningAverage * 0.9);
        if (enabled && settleCounter <= 0) {
            if  (magnitude > sensitivity) {
                ByteBuffer data = ByteBuffer.wrap(new byte[Float.BYTES]);
                data.asFloatBuffer().put(magnitude);

                doTrigger(new QueueTrigger(event.gpsTimestamp, event.systemTimestamp, sensorType,
                        sensorIndex, data));
                settleCounter = SETTLE_SAMPLE_NUM;
            }
        } else
            --settleCounter;
    }
}
