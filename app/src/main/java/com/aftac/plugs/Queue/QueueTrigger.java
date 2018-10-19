package com.aftac.plugs.Queue;

import java.nio.ByteBuffer;

public class QueueTrigger extends Queue.QueueItem {
    @Override int getType() { return Queue.ITEM_TYPE_TRIGGER; }

    String source = Queue.getName();
    long timestamp;
    int sensorType;
    int sensorIndex;
    ByteBuffer data;

    public QueueTrigger(long timestamp, int sensorType, int sensorIndex, ByteBuffer data) {
        super();
        this.timestamp = timestamp;
        this.sensorType = sensorType;
        this.sensorIndex = sensorIndex;
        this.data = data;
    }

    public QueueTrigger(ByteBuffer buffer) {
        super();
        this.timestamp = buffer.getLong();
        this.sensorType = buffer.getInt();
        this.sensorIndex = buffer.getInt();
        this.data = buffer.slice();
    }
}
