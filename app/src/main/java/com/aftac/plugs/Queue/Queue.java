package com.aftac.plugs.Queue;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.aftac.plugs.Gps.GpsService;
import com.aftac.plugs.MeshNetwork.MeshManager;
import com.aftac.plugs.MeshNetwork.MeshManager.MeshStatusChangedListener;
import com.aftac.plugs.PlugsNotification;
import com.aftac.plugs.R;
import com.aftac.plugs.Recorder.CameraHandler;
import com.aftac.plugs.Recorder.RecordingManager;
import com.aftac.plugs.Sensors.PlugsSensorManager;
import com.aftac.plugs.Triggers.AudioTrigger;
import com.aftac.plugs.Triggers.MagnitudeTrigger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

public class Queue extends Service {
    static final String LOG_TAG =  Queue.class.getSimpleName();

    public static final String COMMAND_TARGET_NONE = "%NONE%";
    public static final String COMMAND_TARGET_SELF = "%SELF%";
    public static final String COMMAND_TARGET_ALL =  "%ALL%";

    public final static int COMMAND_CLASS_MISC      = 0;
    public final static int COMMAND_CLASS_SENSORS   = 1;
    public final static int COMMAND_CLASS_TRIGGERS  = 2;
    public final static int COMMAND_CLASS_MESH_NET  = 3;
    public final static int COMMAND_CLASS_BASE_COMM = 4;


    public static final int COMMAND_MISC_STOP = 1;
    public final static int COMMAND_MISC_POKE = 100;

    static final int ITEM_TYPE_NONE = 0;
    static final int ITEM_TYPE_COMMAND = 1;
    static final int ITEM_TYPE_TRIGGER = 2;

    private static PlugsNotification notification;

    private static HandlerThread workThread;
    static Handler mainHandler;
    static Handler workHandler;

    private static SparseArray<Method> miscCommands;
    private static SparseArray<Method> sensorCommands;
    private static SparseArray<Method> meshCommands;

    private static SharedPreferences prefs;

    private static AudioTrigger audioTrigger;
    private static MagnitudeTrigger magnitudeTrigger;

    private static Queue me;
    private static String myName = "Plugs-";
    volatile private static Boolean running = false;
    private static boolean initialized = false;



    public static String setName(String name) { return myName = name; }
    public static String getName() { return myName; }

    static abstract class QueueItem implements Serializable{
        abstract int getType();
        long timestamp = GpsService.getUtcTime();
        String source = myName;
    }


    // onStartedListener
    private static onStartedListener startListener = null;
    public interface onStartedListener { void onQueueStarted(); }
    public static void setStartedListener(onStartedListener listener) { startListener = listener; }

    // No need to bind to the queue service
    @Override public IBinder onBind(Intent intent) { return null; }

    // Returns true if the queue service is running
    public static boolean isRunning() { return running; }

    // Push items to the queue for processing
    // Generic push method so network classes don't need to know the message type
    public static void push(QueueItem item) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("content", item);

        Message msg = new Message();
        msg.what = 1;
        msg.setData(bundle);

        workHandler.sendMessage(msg);
        Log.v(LOG_TAG, "An item was pushed onto the queue.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (running) {
            // Service is already running
            Log.v(LOG_TAG, "Queue service poked.");
            if (startListener != null) startListener.onQueueStarted();
            readIntent(intent);
            return START_STICKY;
        }

        Log.v(LOG_TAG, "Queue service started.");
        running = true;
        me = this;

        if (!initialized) {
            initialized = true;

            // Create a notification to keep this service running in the background
            PlugsNotification notification = new PlugsNotification(getBaseContext());
            startForeground(notification.getId(), notification.get());

            // Get a handler for the main thread
            mainHandler = new Handler(getMainLooper());

            // Create a new work thread and handler for it
            workThread = new HandlerThread(getClass().getName(), Thread.MAX_PRIORITY);
            workThread.start();
            workHandler = new Handler(workThread.getLooper(), workCallback);

            prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(prefsListener);

            // Do the rest of the initialization in the work thread
            workHandler.post(this::init);
        }

        readIntent(intent);
        return START_STICKY;
    }


    @Queue.Command(COMMAND_MISC_POKE)
    public static void poke(QueueCommand cmd) {
        Toast toast = Toast.makeText(me.getBaseContext(), "Pinged from " + cmd.getSource(),
                Toast.LENGTH_SHORT);

        mainHandler.post(toast::show);
    }


    /*
        Privates
    */
    private void init() {
        MeshManager.init(this);
        PlugsSensorManager.init(this.getBaseContext());
        GpsService.init(this.getBaseContext());
        CameraHandler.init();

        loadPreferences();

        int id = (int) (Math.random() * Integer.MAX_VALUE);
        //myName += Integer.toHexString(id & 0xFFFF);

        // Populate the command lists in the work thread
        workHandler.post(() -> {
            miscCommands = getQueueCommands(this.getClass());
            sensorCommands = getQueueCommands(PlugsSensorManager.class);
            meshCommands = getQueueCommands(MeshManager.class);

            if (startListener != null) mainHandler.post(() -> startListener.onQueueStarted());
        });
    }

    // Sends a bundle from an intent to the work thread
    private void readIntent(Intent intent) {
        if (intent != null) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                ByteBuffer buffer = ByteBuffer.wrap(bundle.getByteArray("content"));
                QueueCommand command = new QueueCommand(buffer, true);
                push(command);
            }
        }
    }

    // Routes commands & triggers sent to the work thread
    private Handler.Callback workCallback = new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if (!bundle.containsKey("content")) return false;

            QueueItem content = (QueueItem) bundle.getSerializable("content");
            Log.v(LOG_TAG, "Queue is processing an item (" + content.getType() + ") " + myName);

            switch (content.getType()) {
                case ITEM_TYPE_COMMAND:
                    QueueCommand cmd = (QueueCommand)content;
                    String target = cmd.target;

                    Log.v(LOG_TAG, cmd.source + ", " + cmd.target + ", "
                                + cmd.commandClass + ", " + cmd.commandId);

                    // Forward commands from local device not targeted at self
                    if (cmd.source.equals(myName)
                            && !(target.equals(COMMAND_TARGET_SELF) || target.equals(myName))) {
                        MeshManager.send(cmd.target, MeshManager.CONTENT_COMMAND, cmd.toBytes());
                    }

                    // Process commands targeted at the local device
                    if (target.equals(COMMAND_TARGET_SELF) || target.equals(COMMAND_TARGET_ALL)
                                || target.equals(myName)) {
                        Log.v(LOG_TAG, "Queue is processing a command");
                        try { processCommand(cmd); }
                        catch (Exception e) { Log.e(LOG_TAG, "Exception", e); }
                    } else
                        Log.v(LOG_TAG, "Queue ignored a command not targeted at this device");

                break;
                case ITEM_TYPE_TRIGGER:
                    QueueTrigger trigger = (QueueTrigger)content;

                    if (trigger.source.equals(myName)) {
                        MeshManager.send(MeshManager.DEVICE_ALL, MeshManager.CONTENT_TRIGGER,
                                trigger.toBytes());
                    }

                    processTrigger(trigger);
                break;
            }
            return true;
        }
    };

    private void processTrigger(QueueTrigger trigger) {
        // TODO: process triggers

        //if (trigger.sourceId == myId) {
        //    Send trigger out over network
        //}

        //if command exists for trigger {
        //    processCommand(triggerCommand);
        //}

        //if (deployed) {
            if (prefs.getBoolean("action_video", false))
                RecordingManager.recordVideo();
            if (prefs.getBoolean("action_photo", false))
                RecordingManager.takePicture();
            if (prefs.getBoolean("action_microphone", false))
                RecordingManager.recordAudio();
        //}

        SimpleDateFormat formater = new SimpleDateFormat("HH:mm:ss.SSS");

        Toast toast = Toast.makeText(me.getBaseContext(), "Trigger from: " + trigger.getSource()
                + " at " + formater.format(trigger.timestampUtc), Toast.LENGTH_SHORT);

        mainHandler.post(toast::show);
    }

    // Processes commands sent to the Queue
    private void processCommand(QueueCommand command)
            throws InvocationTargetException, IllegalAccessException {
        SparseArray<Method> list;
        switch (command.commandClass) {
            case COMMAND_CLASS_MISC:
                list = miscCommands;
                break;
            case COMMAND_CLASS_SENSORS:
                list = sensorCommands;
                break;
            case COMMAND_CLASS_MESH_NET:
                list = meshCommands;
                break;

            default:
                // TODO: Invalid Command Class
                return;
        }

        // Get the method to be executed
        Method method = list.get(command.commandId);
        // Get the arguments
        JSONArray jsonArgs = command.args;

        // Determine if the command should be added as an argument
        Class[] methodParms = method.getParameterTypes();
        boolean addCommandArg = false;
        if (methodParms.length > 0 && methodParms[methodParms.length - 1] == QueueCommand.class) {
            if (jsonArgs != null) {
                if (methodParms.length > jsonArgs.length())
                    addCommandArg = true;
            } else
                addCommandArg = true;
        }

        // Turn the argument JSONArray into an Object Array so the arguments get passed properly
        Object[] args = null;
        if (jsonArgs != null){
            int len = jsonArgs.length();
            args = new Object[addCommandArg ? len + 1 : len];
            for (int i = 0; i < len; i++) {
                args[i] = jsonArgs.opt(i);
            }
            if (addCommandArg)
                args[args.length - 1] = command;
        } else if (addCommandArg)
            args = new Object[]{command};

        // Execute the command
        Object returnVal = method.invoke(null, args);

        // Return the response to the listener
        if (command.responseListener != null) {
            command.responseHandler.post(() ->
                    command.responseListener.onCommandResponse(returnVal, command));
        }
        Log.v(LOG_TAG, "Successfully invoked method \""
                + method.getClass() + "." + method.getName() + "\"");
    }

    // Quit the work thread and stop the queue service
    private void stopAndQuit() {
        running = false;
        stopForeground(true);
        workHandler.removeMessages(1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            workThread.quitSafely();
        else
            workThread.quit();

        mainHandler = null;
        workHandler = null;
        workThread = null;
        stopSelf();
        me = null;
        Log.v(LOG_TAG, "Queue service has stopped");
    }

    // Custom annotation to mark queue commands
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Command { int value(); }

    // Queue command to stop the Queue service
    @Queue.Command(COMMAND_MISC_STOP)
    static void stopCommand() { me.stopAndQuit(); }

    // Makes a list of methods from a class that have the Queue.addCommand annotation
    private SparseArray<Method> getQueueCommands(Class src) {
        SparseArray<Method> arr = new SparseArray<>();
        Method[] methods = src.getMethods();

        for (Method method : methods) {
            Command cmd = method.getAnnotation(Command.class);
            if (cmd == null) continue;

            Log.v(LOG_TAG, "Queue command method found: " + method.getName());
            if (!Modifier.isStatic(method.getModifiers())) {
                // Only static methods can be queue commands
                Log.e(LOG_TAG, "Bad queue command definition at \""
                        + src.getName() + "." + method.getName() + "\"");
                Log.e(LOG_TAG, "Queue commands must be static");
            } else if (arr.get(cmd.value()) != null) {
                // Queue command's can't use duplicate IDs
                Log.e(LOG_TAG, "Trying to create queue command with duplicate id \""
                        + cmd.value() + "\" at \""
                        + src.getName() + "." + method.getName() + "\"");
            } else {
                // Make sure the return type is either JSON, or void
                Class returnClass = method.getReturnType();
                if (returnClass != JSONObject.class && returnClass != JSONArray.class
                        && returnClass != void.class) {
                    Log.e(LOG_TAG, "Bad queue command definition at \""
                            + src.getName() + "." + method.getName() + "\"");
                    Log.e(LOG_TAG, "Return type must be JSONArray, JSONObject, or void");
                } else {
                    // This command is good, add it to the list
                    arr.put(cmd.value(), method);
                }
            }
        }
        return arr;
    }

    private static void loadPreferences() {
        // Load name if a setting exists, otherwise generate a random name
        if (prefs.contains("general_device_name")) {
            setName(prefs.getString("general_device_name",
                    me.getString(R.string.pref_default_device_name)));
        } else {
            int id = (int) (Math.random() * Integer.MAX_VALUE);
            myName = "Plugs-" + Integer.toHexString(id & 0xFFFF);
            prefs.edit()
                    .putString("general_device_name", myName)
                    .apply();
        }

        setAccelerometerTrigger(Float.parseFloat(
                prefs.getString("pref_trigger_accelerometer", "0")));

        setAudioTrigger(Float.parseFloat(
                prefs.getString("pref_trigger_microphone", "0")));
    }

    private static SharedPreferences.OnSharedPreferenceChangeListener prefsListener =
                new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            switch (key) {
                case "general_device_name":
                    setName(prefs.getString("general_device_name",
                            me.getString(R.string.pref_default_device_name)));
                break;
                case "pref_trigger_accelerometer":
                    //String[] strValues = me.getResources().getStringArray(R.array.pref_trigger_accelerometer_values);
                    setAccelerometerTrigger(Float.parseFloat(
                            prefs.getString("pref_trigger_accelerometer", "0")));
                break;
                case "pref_trigger_microphone":
                    setAudioTrigger(Float.parseFloat(
                            prefs.getString("pref_trigger_microphone", "0")));
                break;
            }
        }
    };

    private static void setAccelerometerTrigger(float value) {
        if (value == 0) {
            if (magnitudeTrigger != null) {
                magnitudeTrigger.disable();
                magnitudeTrigger.detach();
                magnitudeTrigger = null;
            }
        } else {
            if (magnitudeTrigger == null)
                magnitudeTrigger = new MagnitudeTrigger(Sensor.TYPE_ACCELEROMETER);
            magnitudeTrigger.setCalibration(9.8f);
            switch ((int)value) {
                case 25:  value = 0.1f;    break;
                case 50:  value = 0.05f;   break;
                case 75:  value = 0.025f;  break;
                case 100: value = 0.0125f; break;
            }

            Log.v(LOG_TAG, "Accelerometer: " + value);
            magnitudeTrigger.setSensitivity(value);
            magnitudeTrigger.enable();
        }
    }

    private static void setAudioTrigger(float value) {
        if (value == 0) {
            if (audioTrigger != null) {
                audioTrigger.disable();
                audioTrigger.detach();
                audioTrigger = null;
            }
        } else {
            if (audioTrigger == null)
                audioTrigger = new AudioTrigger();

            switch ((int)value) {
                case 25:  value =  10f; break;
                case 50:  value =   0f; break;
                case 75:  value = -10f; break;
                case 100: value = -20f; break;
            }
            Log.v(LOG_TAG, "Microphone: " + value);
            audioTrigger.setSensitivity(value);
            audioTrigger.enable();
        }
    }
}