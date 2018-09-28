package com.aftac.plugs.Queue;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;

import com.aftac.plugs.MainActivity;
import com.aftac.plugs.Sensors.PlugsSensors;

import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class Queue extends Service {
    private static final String LOG_TAG = "PLUGS.Queue";

    public static final int COMMAND_TARGET_NONE = 0x00000000;
    public static final int COMMAND_TARGET_ALL = 0xFFFFFFFF;

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

    private static Queue me;
    private static PlugsSensors plugsSensors;

    private static int myId = 1;
    volatile private static Boolean running = false;


    public static abstract class QueueItem implements Serializable {
        int getType() { return ITEM_TYPE_NONE; }
        long timestamp;
        int sourceId = myId;
    }

    public static class Command extends QueueItem {
        @Override
        int getType() { return ITEM_TYPE_COMMAND; }
        int queueItemType = ITEM_TYPE_COMMAND;
        int commandClass = -1;
        int targetId = COMMAND_TARGET_NONE;
        int commandId = -1;
        Object[] args;
        Runnable responseListener = null;

        public Command(int targetId, int commandClass, int commandId, Object[] args) {
            this.commandClass = commandClass;
            this.targetId = targetId;
            this.commandId = commandId;
            this.args = args;
        }

        //public void setResponseListener(
        //};
    }

    public static abstract class Trigger extends QueueItem {
        @Override
        int getType() { return ITEM_TYPE_TRIGGER; }
    }

    private static onStartedListener startListener = null;
    public interface onStartedListener { void onQueueStarted(); }
    public static void setStartedListener(onStartedListener obj) { startListener = obj; }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    // No need to bind to the queue service
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    // Returns true if the queue service is running
    public static boolean isRunning() {
        return running;
    }

    // Push items to the queue for processing
    // Generic push method so network classes don't need to know the message type
    public static void push(QueueItem item) {
        push(item, item.getType());
    }

    private static void push(QueueItem item, int type) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("content", item);
        Message msg = new Message();
        msg.what = 1;
        msg.setData(bundle);
        if (!workThread.isAlive())
            workThread.start();
        workHandler.sendMessage(msg);
        Log.v(LOG_TAG, "An item was pushed onto the queue.");
    }

    //
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (!running) {
            Log.v(LOG_TAG, "Queue service started.");
            running = true;
            me = this;

            // Get a handler for the main thread
            mainHandler = new Handler(getMainLooper());

            // Create a work thread and handler for the Queue service
            workThread = new HandlerThread(getClass().getName(), Thread.MAX_PRIORITY);
            workThread.start();
            workHandler = new Handler(workThread.getLooper(), workCallback);

            // TODO: startForeground(id, notification);

            // Do the rest of the initialization in the work thread
            workHandler.post(() -> init());
        } else
            Log.v(LOG_TAG, "Queue service poked.");
        readIntent(intent);
        return START_STICKY;
    }


    /*
        Privates
    */
    private void init() {
        plugsSensors = new PlugsSensors(this.getBaseContext());

        // Populate the command lists in the work thread
        workHandler.post(() -> {
            miscCommands = getQueueCommands(this.getClass());
            sensorCommands = getQueueCommands(PlugsSensors.class);

            mainHandler.post(() -> { if (startListener != null) startListener.onQueueStarted(); });
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
            QueueItem content = (QueueItem) bundle.getSerializable("content");
            Log.v(LOG_TAG, "Queue is processing an item (" + content.getType() + ")");
            switch (content.getType()) {
                case ITEM_TYPE_COMMAND:
                    Log.v(LOG_TAG, "Queue is processing a command");
                    try { processCommand((Command) content); }
                    catch (Exception e) { Log.e(LOG_TAG , "Exception", e); }
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
        //    processCommand(command);
        //}
    }

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
            default:
                // TODO: Invalid Command Class
                return;
        }

        Method method = list.get(command.commandId);
        Object[] args = command.args;
        if (args == null) args = new Object[0];

        Object returnVal = method.invoke(null, args);

        // TODO: handle returned values properly
        method.getReturnType().cast(returnVal);
        //if (command.responseListener != null)
        //    command.responseListener.start();
        Log.v(LOG_TAG, "Successfully invoked method \"" + method.getName() + "\"");
    }

    // Quit the work thread and stop the queue service
    private void stopAndQuit() {
        stopForeground(true);
        workHandler.removeMessages(1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            workThread.quitSafely();
        else
            workThread.quit();

        mainHandler = null;
        workHandler = null;
        workThread = null;
        this.stopSelf();
    }

    // Annotation to mark queue commands
    @Retention(RetentionPolicy.RUNTIME)
    public @interface addCommand {
        int id();
    }


    // This Queue.command annotation for creating queue commands is just an experiment
    // I'm not sure that it will really work out
    @Queue.addCommand(id=COMMAND_MISC_STOP)
    static void stopCommand(Object[] args) {
        me.stopAndQuit();
    }

    // Makes a list of methods from a class that have the above annotation
    private SparseArray<Method> getQueueCommands(Class src) {
        SparseArray<Method> arr = new SparseArray<>();
        Method[] methods = src.getMethods();
        Class<?>[] parTypes;
        Method method;
        for (int i = 0; i < methods.length; i++) {
            method = methods[i];
            addCommand cmd = method.getAnnotation(addCommand.class);
            if (cmd != null) {
                Log.v(LOG_TAG, "Command method found: " + method.getName());
                if (!Modifier.isStatic(method.getModifiers())) {
                    Log.e(LOG_TAG, "Bad Queue command definition at \""
                            + src.getName() + "." + method.getName() + "\"");
                    Log.e(LOG_TAG, "Queue commands must be static");
                } else if (arr.get(cmd.id()) != null) {
                    // TODO: Command already exists with that id
                    Log.e(LOG_TAG, "Trying to create queue command with duplicate id \""
                            + cmd.id() + "\" at \""
                            + src.getName() + "." + method.getName() + "\"");
                } else {
                    Log.v(LOG_TAG, "Command added: " + method.getName());
                    arr.put(cmd.id(), method);
                }
            }
        }
        return arr;
    }
}