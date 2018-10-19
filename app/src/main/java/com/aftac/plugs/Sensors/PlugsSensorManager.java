package com.aftac.plugs.Sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.SparseArray;

import com.aftac.plugs.Queue.Queue;

import org.json.JSONArray;

import java.util.List;

public class PlugsSensorManager {
    static final String LOG_TAG = PlugsSensorManager.class.getSimpleName();
    public static final int COMMAND_GET_SENSORS = 0;

    public static final int STANDARD_ANDROID_SENSOR_MASK = 0x80000000;

    private static int LIST_INITIAL_SIZE = 5;
    private static int LIST_GROW_SIZE = 5;



    private static String className = PlugsSensorManager.class.getName();
    private static SensorManager sensorManager = null;

    private static HandlerThread sensorThread = null;
    private static Handler sensorHandler = null;
    private static HandlerThread workThread = null;
    private static Handler workHandler = null;

    private static SparseArray<SparseArray> sensorWrapperLists= new SparseArray<>();



    // Public interface to listen for data events from the PlugsSensorWrapper
    public interface PlugsSensorEventListener {
        void onPlugsSensorEvent(PlugsSensorEvent event);
    }

    public static void init(Context context) {
        if (sensorManager != null) return;  // Already initialized

        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        // Thread for receiving sensor events
        sensorThread = new HandlerThread(className, Thread.NORM_PRIORITY);
        sensorThread.start();
        sensorHandler = new Handler(sensorThread.getLooper());

        // Default thread for work on sensor events
        workThread = new HandlerThread(className, Thread.NORM_PRIORITY);
        workThread.start();
        workHandler = new Handler(sensorThread.getLooper());
    }

    @Queue.Command(COMMAND_GET_SENSORS)
    static public JSONArray getSensors(int type, String test) {
        Log.v(LOG_TAG, "Test string: " + test);
        List<Sensor> list = sensorManager.getSensorList(type);
        JSONArray ret = new JSONArray();
        for (int i = 0; i < list.size(); i++) {
            ret.put(list.get(i));
        }
        return ret;
    }

    // Add a listener to a plugs sensor's data events
    public static void addSensorEventListener(int type, int index,
                                              PlugsSensorEventListener listener, Handler handler) {
        PlugsSensorWrapper sensor = getSensor(type, index);
        if (!sensor.isRunning()) sensor.start();
        sensor.addEventListener(listener, handler);
    }
    public static void addSensorEventListener(int type, PlugsSensorEventListener listener,
                                      Handler handler) {
        addSensorEventListener(type, -1, listener, handler);
    }
    public static void addSensorEventListener(int type, int index,
                                              PlugsSensorEventListener listener) {
        addSensorEventListener(type, index, listener, workHandler);
    }
    public static void addSensorEventListener(int type, PlugsSensorEventListener listener) {
        addSensorEventListener(type, -1, listener, workHandler);
    }

    // Remove a data event listener from a plug sensor
    public static void removeSensorEventListener(int type, int index, PlugsSensorEventListener listener) {
        PlugsSensorWrapper sensor = getSensor(type, index);

        sensor.removeEventListener(listener);
        if (sensor.listeners.size() <= 0) sensor.stop();
    }


    // Privates
    private static PlugsSensorWrapper getSensor(int type, int index) {
        SparseArray<PlugsSensorWrapper> subList;
        PlugsSensorWrapper sensor;

        if (index == -1) index = getDefaultSensorIndex(type);
        if ((subList = sensorWrapperLists.get(type)) == null) {
            subList = new SparseArray<>();
            sensorWrapperLists.put(type, subList);
        }
        if ((sensor = subList.get(index)) == null) {
            sensor = new PlugsSensorWrapper(sensorManager, type, index, sensorHandler, workHandler);
            subList.put(index, sensor);
        }

        return sensor;
    }

    private static int getDefaultSensorIndex(int type) {
        int index = -1;
        List<Sensor> androidSensors = sensorManager.getSensorList(type);
        Sensor defaultSensor = sensorManager.getDefaultSensor(type);
        while (androidSensors.get(++index) != defaultSensor);
        return index;
    }
}