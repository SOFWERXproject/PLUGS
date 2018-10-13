package com.aftac.plugs.Queue;

public class QueueTrigger extends Queue.QueueItem {
    @Override int getType() { return Queue.ITEM_TYPE_TRIGGER; }
}
