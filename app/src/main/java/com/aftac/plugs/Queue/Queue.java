package com.aftac.plugs.Queue;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import com.aftac.plugs.MeshNetwork.MeshManager;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Queue extends Service {
    private static final String LOG_TAG =  Queue.class.getSimpleName();

    public static final int COMMAND_TARGET_NONE = 0x00000000;
    public static final int COMMAND_TARGET_SELF = 0xFFFFFFFE;
    public static final int COMMAND_TARGET_ALL =  0xFFFFFFFF;

    public final static int COMMAND_CLASS_MISC = 0;
    public final static int COMMAND_CLASS_SENSORS = 1;
    public final static int COMMAND_CLASS_TRIGGERS = 2;
    public final static int COMMAND_CLASS_MESH_NET = 3;
    public final static int COMMAND_CLASS_BASE_COM = 4;

    public static final int COMMAND_MISC_STOP = 1;

    private static final int ITEM_TYPE_NONE = 0;
    private static final int ITEM_TYPE_COMMAND = 1;
    private static final int ITEM_TYPE_TRIGGER = 2;

    private static Handler mainHandler;
    private static Handler workHandler;
    private static HandlerThread workThread;

    private static SparseArray<Method> miscCommands;
    private static SparseArray<Method> sensorCommands;
    private static SparseArray<Method> meshCommands;

    private static Queue me;

    private static String name = "Plugs-";
    private static int myId = 1;
    volatile private static Boolean running = false;
    private static boolean initialized = false;


    public static String getName() { return name; }

    public static abstract class QueueItem implements Serializable {
        int getType() { return ITEM_TYPE_NONE; }
        long timestamp;
        int sourceId = myId;
    }

    public static class Command extends QueueItem {
        @Override
        int getType() { return ITEM_TYPE_COMMAND; }
        int commandClass;
        int targetId;
        int commandId;
        JSONArray args;
        CommandResponseListener responseListener = null;
        Handler  responseHandler = workHandler;

        public Command(int targetId, int commandClass, int commandId, JSONArray args) {
            this.commandClass = commandClass;
            this.targetId  = targetId;
            this.commandId = commandId;
            this.args = args;
        }
        public Command(ByteBuffer buf) {
            targetId     = buf.getInt();
            commandClass = buf.getInt();
            commandId    = buf.getInt();
            try {
                ByteBuffer sliced = buf.slice();
                byte[] bytes = new byte[sliced.remaining()];
                sliced.get(bytes);
                String strJSON = new String(bytes, "UTF-8");
                Log.v(LOG_TAG, "Command parsed: " + targetId + ", " + commandClass + ", "
                            + "commandId");
                Log.v(LOG_TAG, strJSON);
                args = new JSONArray(strJSON);
            } catch (Exception e) { e.printStackTrace(); }
        }

        public byte[] toBytes() {
            ByteBuffer ret;

            String argStr = args.toString();
            ret = ByteBuffer.wrap(new byte[12 + argStr.length() + 1]);

            ret.putInt(targetId);
            ret.putInt(commandClass);
            ret.putInt(commandId);
            ret.put(argStr.getBytes());
            ret.put((byte)0);

            return ret.array();
        }

        // A handler can be set for the responseListener, or just make one for the calling thread
        public void setResponseListener(CommandResponseListener listener, Handler handler) {
            responseListener = listener;
            responseHandler  = handler;
        }
        public void setResponseListener(CommandResponseListener listener) {
            setResponseListener(listener, new Handler(Looper.myLooper()));
        }
    }
    public interface CommandResponseListener {
        void onCommandResponse(Object response, Command cmd);
    }

    public static abstract class Trigger extends QueueItem {
        @Override
        int getType() { return ITEM_TYPE_TRIGGER; }

        //abstract void Trigger(ByteBuffer buf);
    }

    private static onStartedListener startListener = null;
    public interface onStartedListener { void onQueueStarted(); }
    public static void setStartedListener(onStartedListener obj) { startListener = obj; }


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

            // Get a handler for the main thread
            mainHandler = new Handler(getMainLooper());

            // Create a work thread and handler for the Queue service
            workThread = new HandlerThread(getClass().getName(), Thread.MAX_PRIORITY);
            workThread.start();
            workHandler = new Handler(workThread.getLooper(), workCallback);

            // TODO: startForeground(id, notification);

            // Do the rest of the initialization in the work thread
            workHandler.post(this::init);
        }

        readIntent(intent);
        return START_STICKY;
    }


    /*
        Privates
    */
    private void init() {
        PlugsSensorManager.init(this.getBaseContext());

        int id = (int) (Math.random() * Integer.MAX_VALUE);
        name += Integer.toHexString(id & 0xFFFF);

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
                Message msg = new Message();
                msg.setData(bundle);
                workHandler.sendMessage(msg);
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
            Log.v(LOG_TAG, "Queue is processing an item (" + content.getType() + ")");

            switch (content.getType()) {
                case ITEM_TYPE_COMMAND:
                    int target = ((Command)content).targetId;
                    if (target == myId || target == COMMAND_TARGET_SELF
                            || target == COMMAND_TARGET_ALL) {
                        Log.v(LOG_TAG, "Queue is processing a command");
                        try { processCommand((Command) content); }
                        catch (Exception e) { Log.e(LOG_TAG, "Exception", e); }
                    } else
                        Log.v(LOG_TAG, "Queue ignored a command not targeted at this device");
                    break;

                case ITEM_TYPE_TRIGGER:
                    processTrigger((Trigger) content);
                    break;
            }
            return true;
        }
    };

    private void processTrigger(Trigger trigger) {
        // TODO: process triggers

        //if (trigger.sourceId == myId) {
        //    Send trigger out over network
        //}

        //if command exists for trigger {
        //    processCommand(triggerCommand);
        //}
    }

    // Processes commands sent to the Queue
    private void processCommand(Command command)
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

        // Turn the argument JSONArray into an Object Array so the arguments get passed properly
        JSONArray jsonArgs = command.args;
        Object[] args = null;
        if (jsonArgs != null){
            int len = jsonArgs.length();
            args = new Object[len];
            for (int i = 0; i < len; i++) {
                args[i] = jsonArgs.opt(i);
            }
        }

        // Execute the command
        Method method = list.get(command.commandId);
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
    public @interface addCommand {
        int value();
    }

    // Queue command to stop the Queue service
    @Queue.addCommand(COMMAND_MISC_STOP)
    static void stopCommand() {
        me.stopAndQuit();
    }

    // Makes a list of methods from a class that have the Queue.addCommand annotation
    private SparseArray<Method> getQueueCommands(Class src) {
        SparseArray<Method> arr = new SparseArray<>();
        Method[] methods = src.getMethods();

        for (Method method : methods) {
            addCommand cmd = method.getAnnotation(addCommand.class);
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
}