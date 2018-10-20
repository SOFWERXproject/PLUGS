package com.aftac.plugs.Sensors;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

// Class to send data to listeners of PlugsSensors
public class PlugsSensorEvent {
    public ByteBuffer data;
    
    public int     sensorType;
    public int     sensorIndex;
    public boolean standardSensor;
    public long    timestamp;
    public int     accuracy;
    public float[] values;

    PlugsSensorEvent(long timestamp, int sensorIndex, int sensorType, int accuracy,
                     ByteBuffer data){
        this.timestamp = timestamp;
        this.sensorIndex = sensorIndex;
        this.sensorType = sensorType;
        this.accuracy = accuracy;
        data.rewind();
        data.order(ByteOrder.LITTLE_ENDIAN);
        this.data = data.slice();
        if ((sensorIndex & PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK) != 0) {
            FloatBuffer floatBuf = data.asFloatBuffer();

            values = new float[floatBuf.limit()];
            floatBuf.get(values);
        }
    }

    PlugsSensorEvent(ByteBuffer data) {
        this.data = data;
        
        sensorIndex = data.getInt();
        standardSensor = ((sensorType & PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK) != 0);
        sensorIndex &= ~PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK;
        sensorType = data.getInt();
        timestamp = data.getLong();
        accuracy = data.getInt();
        
        if (standardSensor) {
            FloatBuffer floatBuf = data.slice().asFloatBuffer();
            values = new float[floatBuf.limit() - floatBuf.position()];
            floatBuf.get(values);
        }
        
        data.mark();
    }
}