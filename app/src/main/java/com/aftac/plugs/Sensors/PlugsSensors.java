package com.aftac.plugs.Sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.List;

import com.aftac.plugs.Queue.Queue;

import org.json.JSONArray;


public class PlugsSensors {
    public static final String LOG_TAG = "PLUGS.PlugsSensors";
    public static final int COMMAND_GET_SENSORS = 0;

    private static SensorManager sensorManager;


    public PlugsSensors(Context context) {
        sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
    }

    // The Queue.command annotation for creating queue commands is just an experiment
    // I'm not sure that it will really work out
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
}
