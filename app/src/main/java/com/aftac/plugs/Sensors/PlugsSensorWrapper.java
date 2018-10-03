package com.aftac.plugs.Sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

// A wrapper class for reading Android sensors
class PlugsSensorWrapper implements SensorEventListener {
    private int id;
    private SensorManager sensorManager;
    private Sensor sensor;
    private int sensorType;
    private Handler sensorHandler;
    private Handler workHandler;
    final List<PlugsSensorManager.PlugsSensorEventListenerCallback> listeners = new ArrayList<>();

    private volatile boolean running = false;
    private volatile boolean nulled = true;

    boolean isRunning() { return running; }
    boolean isFree() { return nulled; }
    Sensor getSensor() { return sensor; }


    PlugsSensorWrapper(SensorManager sensorManager, int id, Handler sensorHandler,
                       Handler workHandler) {
        this.id = id;
        this.sensorManager = sensorManager;
        this.sensorHandler = sensorHandler;
        this.workHandler = workHandler;
    }

    // Set the sensor to wrap
    void setSensor(Sensor sensor) {
        if (!nulled) free();
        this.sensor = sensor;
        this.sensorType = sensor.getType();
    }

    // Start reading data from a sensor
    boolean start() {
        if (nulled) return false;
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME,
                sensorHandler);
        return running = true;
    }

    // Stop reading sensor
    void stop() {
        sensorManager.unregisterListener(this, sensor);
        running = false;
    }

    // Free this Sensor wrapper so it can be recycled
    void free() {
        synchronized (listeners) { synchronized (this) {
            if (running) stop();    // Stop before freeing
            sensor = null;        // To allow garbage collection
            while (listeners.size() > 0) listeners.remove(0);
            nulled = true;
        } }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listeners.size() == 0) return;

        // TODO: Change to use PLUGS mesh network timestamp
        long utcTimestamp = System.currentTimeMillis();

        // Sometimes Android holds on to sensor events for a while before sending them to apps
        // we have to adjust our timestamp to compensate for this.
            /*
            long milliOffset = (event.timestamp / 1000000) - SystemClock.elapsedRealtime();
            if (milliOffset < bestMilliOffset)
                bestMilliOffset = milliOffset;
            utcTimestamp -= milliOffset - bestMilliOffset;
            */

        // Create a byte array to hold the sensor event data
        byte[] data = new byte[
                                    Integer.BYTES * 3
                                  + Long.BYTES * 2
                                  + Float.BYTES * event.values.length];

        // Write the sensor event data into the array through a ByteBuffer
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(id | 0x80000000); // The 0x80000000 is to mark this as a standard Android sensor
        buf.putInt(sensorType);
        buf.putLong(event.timestamp);
        buf.putLong(utcTimestamp);
        buf.putInt(event.accuracy);
        for (float value : event.values) buf.putFloat(value);

        // Create a bundle to hold the data array
        Bundle bundle = new Bundle();
        bundle.putByteArray("data", data);

        // Package the bundle into a message, and send it to the work handler
        Message msg = new Message();
        msg.setData(bundle); msg.what = 0;
        workHandler.sendMessage(msg);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}