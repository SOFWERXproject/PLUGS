package com.aftac.plugs.Sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemClock;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

// A wrapper class for reading Android sensors
class PlugsSensorWrapper implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private int type;
    private int index;
    private Handler sensorHandler;
    private Handler workHandler;
    final List<PlugsSensorManager.PlugsSensorEventListener> listeners = new ArrayList<>();
    final List<Handler> handlers = new ArrayList<>();

    private volatile boolean running = false;
    private volatile boolean nulled = true;
    private long bestMilliOffset = Long.MAX_VALUE;

    boolean isRunning() { return running; }
    boolean isFree() { return nulled; }
    //Sensor getSensor() { return sensor; }


    PlugsSensorWrapper(SensorManager sensorManager, int type, int index, Handler sensorHandler,
                       Handler workHandler) {
        this.type = type;
        this.index = index;
        this.sensorManager = sensorManager;
        this.sensorHandler = sensorHandler;
        this.workHandler = workHandler;
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
            while (listeners.size() > 0) {
                listeners.remove(0);
                handlers.remove(0);
            }
            sensor = null;        // To allow garbage collection
            sensorManager = null;
            sensorHandler = null;
            nulled = true;
        } }
    }

    void addEventListener(PlugsSensorManager.PlugsSensorEventListener listener, Handler handler) {
        if (!nulled && !listeners.contains(listener)) {
            listeners.add(listener);
            handlers.add(handler);
        }
    }

    void removeEventListener(PlugsSensorManager.PlugsSensorEventListener listener) {
        int index = listeners.indexOf(listener);
        listeners.remove(index);
        handlers.remove(index);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listeners.size() == 0) return;

        // Sometimes Android holds on to sensor events for a while before sending them to apps
        // we have to adjust our timestamp to compensate for this.
        long milliOffset = (event.timestamp / 1000000) - SystemClock.elapsedRealtime();
        if (milliOffset < bestMilliOffset)
            bestMilliOffset = milliOffset;

        // TODO: Change to use PLUGS mesh network timestamp
        final long utcTimestamp = System.currentTimeMillis() - (milliOffset - bestMilliOffset);

        // Create a byte array to hold the sensor event data
        byte[] data;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
            data = new byte[Float.BYTES * event.values.length];
        else
            data = new byte[+ 4 * event.values.length];

        // Create a ByteBuffer to hold the sensor event data
        final ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // Use slice to get a reference sharing the same buffer starting at it's current position
        // then use it as a FloatBuffer to write the values float array into it.
        buf.slice().asFloatBuffer().put(event.values);

        workHandler.post(()-> {
            // Reset buffer position
            buf.reset();
            // Create a PlugsSensorEvent
            PlugsSensorEvent thisEvent = new PlugsSensorEvent(utcTimestamp,
                    index | PlugsSensorManager.STANDARD_ANDROID_SENSOR_MASK,
                    type, event.accuracy, buf);

            for (int i = 0; i < listeners.size(); i++) {
                PlugsSensorManager.PlugsSensorEventListener listener = listeners.get(i);
                Handler handler = handlers.get(i);
                if (handler == workHandler) {
                    listener.onPlugsSensorEvent(thisEvent);
                } else {
                    handler.post(() -> {
                        listener.onPlugsSensorEvent(thisEvent);
                    });
                }
            }
        });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
