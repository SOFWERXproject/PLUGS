package com.aftac.plugs.Sensors;

import java.nio.ByteBuffer;

// Class to send data to listeners of PlugsSensors
public class PlugsSensorEvent {
    public ByteBuffer data;

    public int sensorId;
    public boolean standardSensor;
    public int sensorType;
    public long timestamp;
    public long utcTimestamp;
    int accuracy;

    PlugsSensorEvent(ByteBuffer data) {
        this.data = data;

        sensorId = data.getInt();
        standardSensor = ((sensorId & PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK) != 0);
        sensorId &= ~PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK;
        sensorType = data.getInt();
        timestamp = data.getLong();
        utcTimestamp = data.getLong();
        accuracy = data.getInt();
    }
}
