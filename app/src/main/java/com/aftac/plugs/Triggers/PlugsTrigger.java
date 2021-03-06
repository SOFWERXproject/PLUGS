package com.aftac.plugs.Triggers;

import android.os.Handler;
import android.os.Looper;

import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.Queue.QueueTrigger;
import com.aftac.plugs.Sensors.PlugsSensorEvent;
import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.util.ArrayList;

abstract public class PlugsTrigger {
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
        for (int i = 0; i < triggerListeners.size(); i++) {
            TriggerListener listener = triggerListeners.get(i);
            handlers.get(i).post(()->listener.onTrigger(trigger));
        }
        Queue.push(trigger);
    }

    abstract public void enable();
    abstract public void disable();
    abstract public void setSensitivity(float sensitivity);
}
