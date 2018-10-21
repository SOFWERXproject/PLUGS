package com.aftac.plugs.Triggers;

import android.util.Log;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.nio.ByteBuffer;

public class MagnitudeTrigger extends PlugsTrigger {
    private static final int SETTLE_SAMPLE_NUM = 100;

    int sensorType;
    int sensorIndex = -1;
    float sensitivity;

    public float lastValue;
    //float runningAverage = 0;

    int settleSamples = SETTLE_SAMPLE_NUM;
    boolean enabled = false;

    public MagnitudeTrigger(int sensorType, int sensorIndex) {
        PlugsSensorManager.addSensorEventListener(sensorType, sensorIndex, this);
    }
    public MagnitudeTrigger(int sensorType) {
        PlugsSensorManager.addSensorEventListener(sensorType, this);
    }

    public boolean isEnabled() { return enabled; }
    public void enable()  { enabled = true;  settleSamples = SETTLE_SAMPLE_NUM; }
    public void disable() { enabled = false; }

    public void setSensitivity(float sensitivity) {
        this.sensitivity = sensitivity;
        settleSamples = SETTLE_SAMPLE_NUM;
    }

    @Override
    public void onPlugsSensorEvent(PlugsSensorEvent event) {
        float[] values = event.values;
        float magnitude = (float) Math.sqrt(values[0] * values[0]
                                          + values[1] * values[1]
                                          + values[2] * values[2]);
        //runningAverage = (float) ((magnitude * 0.1) + runningAverage * 0.9);
        if (enabled && settleSamples <= 0) {
            if  (magnitude > sensitivity) {
                ByteBuffer data = ByteBuffer.wrap(new byte[Float.BYTES]);
                data.asFloatBuffer().put(magnitude);

                doTrigger(new QueueTrigger(event.gpsTimestamp, sensorType, sensorIndex,
                        data));

            }
            settleSamples = SETTLE_SAMPLE_NUM;
        } else
            --settleSamples;
    }
}
