package com.aftac.plugs.Queue;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;

import com.aftac.plugs.Triggers.TriggerMessage;

import java.io.Serializable;

public class Queue extends Service {
    private static final int MESSAGE_COMMAND = 1;
    private static final int MESSAGE_TRIGGER = 2;

    private static Handler       mainHandler;
    private static Handler       workHandler;
    private static HandlerThread mainThread;
    private static HandlerThread workThread;

    volatile private static Boolean running = false;


    // No need to bind to the queue service
    @Override
    public IBinder onBind(Intent intent) { return null; }

    // Returns true if the queue service is running
    public static boolean isRunning() { return running; }

    // Receive commands and send them to the work thread
    public static void sendCommand(QueueCommand command) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("command", command);
        Message msg = new Message();
        msg.what = MESSAGE_COMMAND;
        msg.setData(bundle);
        workHandler.sendMessage(msg);
    }

    // Receive triggers, and send them to the work thread
    // TODO: replace Serializable with Trigger object
    public static void sendTrigger(TriggerMessage trigger) {
        Bundle bundle = new Bundle();
        bundle.putSerializable("trigger", trigger);
        Message msg = new Message();
        msg.what = MESSAGE_TRIGGER;
        msg.setData(bundle);
        workHandler.sendMessage(msg);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;

        // Get a handler for the main thread
        mainHandler = new Handler(getMainLooper());

        // Create a work thread and handler for the Queue service
        workThread = new HandlerThread(getClass().getName(), Thread.MAX_PRIORITY);
        workThread.start();
        workHandler = new Handler(workThread.getLooper(), workCallback);
    }

    //
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        readIntent(intent);
        return START_STICKY;
    }


    /*
        Privates
    */

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
            switch (msg.what) {
                case MESSAGE_COMMAND:
                    processCommand((QueueCommand)bundle.getSerializable("command"));
                break;
                case MESSAGE_TRIGGER:
                    processTrigger((TriggerMessage)bundle.getSerializable("trigger"));
                break;
            }
            return true;
        }
    };

    private void processCommand(QueueCommand command) {
        // TODO: process commands
    }

    private void processTrigger(TriggerMessage trigger) {
        // TODO: process triggers
    }

    // Quit the work thread and stop the queue service
    private void stopAndQuit() {
        workHandler.removeMessages(MESSAGE_COMMAND);
        workHandler.removeMessages(MESSAGE_TRIGGER);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2)
            workThread.quitSafely();
        else
            workThread.quit();

        mainHandler = null;
        workHandler = null;
        workThread = null;
        this.stopSelf();
    }
}
