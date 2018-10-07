package com.aftac.plugs.Sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.aftac.plugs.Queue.Queue;

import org.json.JSONArray;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class PlugsSensorManager {
    private static final String LOG_TAG = PlugsSensorManager.class.getSimpleName();
    public static final int COMMAND_GET_SENSORS = 0;

    private static int LIST_INITIAL_SIZE = 5;
    private static int LIST_GROW_SIZE = 5;

    static int STANDARD_ANDROID_SENSOR_MASK = 0x80000000;

    private static String className = PlugsSensorManager.class.getName();
    private static SensorManager sensorManager = null;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static HandlerThread workThread = null;
    private static Handler workHandler = null;

    private static PlugsSensorWrapper[] sensorWrappers = null;



    // Public interface to listen for data events from the PlugsSensorWrapper
    interface PlugsSensorEventListener {
        void onPlugsSensorEvent(PlugsSensorEvent event);
    }

    public static void init(Context context) {
        if (sensorManager != null) return;  // Already initialized

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Thread for receiving sensor events
        sensorThread = new HandlerThread(className, Thread.NORM_PRIORITY);
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        // Thread for work on sensor events
        workThread = new HandlerThread(className, Thread.NORM_PRIORITY);
        workThread.start();
        workHandler = new Handler(sensorThread.getLooper(), workHandlerCallback);
    }

    @Queue.addCommand(COMMAND_GET_SENSORS)
    static public JSONArray getSensors(int type, String test) {
        Log.v(LOG_TAG, "Test string: " + test);
        List<Sensor> list = sensorManager.getSensorList(type);
        JSONArray ret = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            ret.put(list.get(i));
        }
        return ret;
    }

    // Creates a listener for a sensor, and returns an id to reference it by later
    public int initSensor(int type, int index) {
        Sensor sensor = null;
        List<Sensor> list = sensorManager.getSensorList(type);

        if (list == null || list.size() <= index)
            return -1;
        else return createSensorListener(list.get(index));
    }
    public int initSensor(int type) {
        return createSensorListener(sensorManager.getDefaultSensor(type));
    }

    // Start reading data from a sensor
    public boolean startSensor(int id) {
        if (id >= sensorWrappers.length) return false;
        PlugsSensorWrapper wrapper = sensorWrappers[id];
        synchronized (wrapper) { return sensorWrappers[id].start(); }
    }

    // Stop reading data from a sensor
    public void stopSensor(int id) {
        if (id >= sensorWrappers.length) return;
        PlugsSensorWrapper wrapper = sensorWrappers[id];
        synchronized (wrapper) { sensorWrappers[id].stop(); }
    }

    // Free a sensor wrapper
    public void freeSensor(int id) {
        if (id >= sensorWrappers.length) return;
        PlugsSensorWrapper wrapper = sensorWrappers[id];
        synchronized (wrapper) { sensorWrappers[id].free(); }
    }

    public boolean isSensorRunning(int id) {
        return ((id < sensorWrappers.length) ? sensorWrappers[id].isRunning() : false);
    }

    // Add a listener to a plugs sensor's data events
    public void addSensorEventListener(int sensorId, PlugsSensorEventListener listener,
                                      Handler handler) {
        if (sensorId < sensorWrappers.length) {
            PlugsSensorWrapper wrapper = sensorWrappers[sensorId];
            synchronized (wrapper.listeners) { synchronized (wrapper) {
                if (!wrapper.isFree()) {
                    sensorWrappers[sensorId].listeners.add(
                            new PlugsSensorEventListenerCallback(listener, handler));
                }
            } }
        }
    }
    public void addSensorEventListener(int sensorId, PlugsSensorEventListener listener) {
        addSensorEventListener(sensorId, listener, new Handler(Looper.myLooper()));
    }

    // Remove a data event listener from a plug sensor
    public void removeSensorEventListener(int sensorId, PlugsSensorEventListener listener) {
        if (sensorId < sensorWrappers.length) {
            PlugsSensorWrapper wrapper = sensorWrappers[sensorId];
            synchronized (wrapper.listeners) {
                sensorWrappers[sensorId].listeners.remove(listener);
            }
        }
    }


    // Privates

    class PlugsSensorEventListenerCallback {
        WeakReference<PlugsSensorEventListener> listenerRef;
        Handler handler;
        PlugsSensorEventListenerCallback(PlugsSensorEventListener listener, Handler handler) {
            this.listenerRef = new WeakReference(listener);
            this.handler = handler;
        }
    }

    // Creates a sensor listener, and returns it's id
    private int createSensorListener(Sensor sensor) {
        int i, sensorId = -1;
        // If the sensorWrappers array doesn't exist yet, create it
        if (sensorWrappers == null) {
            sensorWrappers = new PlugsSensorWrapper[LIST_INITIAL_SIZE];
            sensorId = 0;
        } else {
            int length = sensorWrappers.length;

            // Find an empty slot in the sensorWrappers array
            // SensorListeners that have been freed can be reused too
            for (i = 0; i < length; i++) {
                if (sensorWrappers[i] == null) {
                    sensorId = i;
                    break;
                } else if (sensorWrappers[i].isFree())
                    sensorId = i;
            }

            // If no sensor Id was found, the list needs to be expanded
            if (sensorId < 0) {
                // Create a new array with an additional 10 slots
                PlugsSensorWrapper[] newArray = new PlugsSensorWrapper[length * LIST_GROW_SIZE];

                // Copy the current sensorWrappers array into the new one, then swap to the new one
                System.arraycopy(sensorWrappers, 0, newArray, 0, length);
                sensorWrappers = newArray;

                sensorId = length;
                length *= 2;
            }
        }

        // If the selected slot is null, create a listener for it
        if (sensorWrappers[sensorId] == null) {
            sensorWrappers[sensorId] = new PlugsSensorWrapper(sensorManager, sensorId,
                    sensorHandler, workHandler);
        }

        // Set the sensor listener's sensor
        sensorWrappers[sensorId].setSensor(sensor);

        return sensorId;
    }

    private static Handler.Callback workHandlerCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            int sensorId = bundle.getInt("sensorId");
            PlugsSensorWrapper wrapper = sensorWrappers[sensorId];

            synchronized (wrapper.listeners) {
                ByteBuffer data = ByteBuffer.wrap(msg.getData().getByteArray("data"));
                PlugsSensorEvent event = new PlugsSensorEvent(data);

                data.order(ByteOrder.LITTLE_ENDIAN);
                for (PlugsSensorEventListenerCallback listenerCallback : wrapper.listeners) {
                    PlugsSensorEventListener listener = listenerCallback.listenerRef.get();
                    listener.onPlugsSensorEvent(event);
                }
            }

            return false;
        }
    };
}
