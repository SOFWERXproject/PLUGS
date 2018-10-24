package com.aftac.plugs.Queue;

import android.util.Log;

import com.aftac.plugs.Sensors.PlugsSensorManager;

import java.nio.ByteBuffer;

public class QueueTrigger extends Queue.QueueItem {
    @Override int getType() { return Queue.ITEM_TYPE_TRIGGER; }

    String source = Queue.getName();
    long timestampUtc;
    long timestampSystem;
    int sensorType;
    int sensorIndex;
    ByteBuffer data;

    public QueueTrigger(long timestampUtc, long timestampSystem, int sensorType, int sensorIndex, ByteBuffer data) {
        super();
        this.timestampUtc    = timestampUtc;
        this.timestampSystem = timestampSystem;
        this.sensorType  = sensorType;
        this.sensorIndex = sensorIndex;
        this.data = data;
    }

    public QueueTrigger(ByteBuffer buffer) {
        super();
        byte chr;
        this.source = ""; while ((chr = buffer.get()) != 0) { this.source += (char)chr; }
        if (source.equals(Queue.getName())) source = Queue.COMMAND_TARGET_NONE;
        this.timestampUtc    = buffer.getLong();
        this.timestampSystem = buffer.getLong();
        this.sensorType  = buffer.getInt();
        this.sensorIndex = buffer.getInt();
        this.data = buffer.slice();
    }

    public String getSource() { return source; }

    public byte[] toBytes() {
        data.rewind();
        ByteBuffer ret = ByteBuffer.wrap(new byte[24 + source.length() + 1 + data.remaining()]);

        try {
            ret.put(source.getBytes()); ret.put((byte)0);
        } catch (Exception e) { e.printStackTrace(); }
        ret.putLong(timestampUtc);
        ret.putLong(timestampSystem);
        ret.putInt(sensorType);
        ret.putInt(sensorIndex);

        ret.put(data);
        return ret.array();
    }
}
