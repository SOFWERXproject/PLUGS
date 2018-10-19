package com.aftac.plugs.Triggers;

import android.os.Handler;
import android.os.Looper;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.util.ArrayList;

abstract public class PlugsTrigger implements PlugsSensorManager.PlugsSensorEventListener {
    ArrayList<TriggerListener> triggerListeners = new ArrayList<>();
    ArrayList<Handler> handlers = new ArrayList<>();

    public interface TriggerListener {
        void onTrigger(QueueTrigger trigger);
    }

    public void addTriggerListener(TriggerListener listener, Handler handler) {
        if (!triggerListeners.contains(listener)) {
            triggerListeners.add(listener);
            handlers.add(handler);
        }
    }
    public void addTriggerListener(TriggerListener listener) {
        addTriggerListener(listener, new Handler(Looper.myLooper()));
    }

    public void doTrigger(QueueTrigger trigger) {
        Queue.push(trigger);
    }

    @Override
    public abstract void onPlugsSensorEvent(PlugsSensorEvent event);
}
