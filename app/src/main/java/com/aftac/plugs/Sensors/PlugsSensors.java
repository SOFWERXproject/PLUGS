package com.aftac.plugs.Sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.List;

import com.aftac.plugs.Queue.Queue;


public class PlugsSensors {
    public static final String LOG_TAG = "PLUGS.PlugsSensors";
    public static final int COMMAND_GET_SENSORS = 0;

    private static SensorManager sensorManager;


    public PlugsSensors(Context context) {
        sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
    }

    static public List<Sensor> getSensors(int type) {
        return sensorManager.getSensorList(type);
    }


    // The Queue.command annotation for creating queue commands is just an experiment
    // I'm not sure that it will really work out
    @Queue.addCommand(id=COMMAND_GET_SENSORS)
    static public List<Sensor> queueCommand_GetSensors(int type, String test) {
        Log.v(LOG_TAG, "Test string: " + test);
        return sensorManager.getSensorList(type);
    }
}
