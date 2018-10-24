package com.aftac.plugs.MeshNetwork;

import android.util.Log;

import com.aftac.plugs.Queue.Queue;
import com.google.android.gms.nearby.connection.Payload;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MeshPacket {
    static int packetCounter = 0;
    int index;
    String source, sourceId;
    String destination;
    ArrayList<String> sentToList;
    int contentType;
    ByteBuffer contents;

    MeshPacket(String destination, int contentType, ByteBuffer data) {
        this.index = ++packetCounter;
        if (this.index < 0) this.index = packetCounter = 1;

        this.source = Queue.getName();
        this.sourceId = "";
        this.destination = destination;
        this.contentType = contentType;
        this.contents = data;

        sentToList = new ArrayList<>();
        contents = data;
    }

    MeshPacket(String deviceId, ByteBuffer data) {
        byte chr;
        data.rewind();

        int headerLength = data.getInt();
        index = data.getInt();
        source = ""; while ((chr = data.get()) != 0) { source += (char)chr; }
        sourceId = ""; while ((chr = data.get()) != 0) { sourceId += (char)chr; }
        if (sourceId.equals("")) sourceId = deviceId;
        destination = ""; while ((chr = data.get()) != 0) { destination += (char)chr; }
        contentType = data.getInt();

        sentToList = new ArrayList<>();
        int sentToLength = data.getInt();
        String name;
        for (int i = 0; i < sentToLength; i++) {
            name = ""; while ((chr = data.get()) != 0) { name += (char)chr; }
            sentToList.add(name);
        }

        data.position(headerLength);
        contents = data.slice();
    }

    byte[] toBytes() {
        ByteBuffer buf;
        if (contents != null) contents.rewind();

        int headerLength = 16 + source.length() + sourceId.length() + destination.length() + 3;
        int sentToLength = sentToList.size();
        int length;

        for (int i = 0; i < sentToLength; i++) {
            headerLength += sentToList.get(i).length() + 1;
        }
        if (contents != null)
            length = headerLength + contents.remaining();
        else
            length = headerLength;


        buf = ByteBuffer.wrap(new byte[length]);
        buf.putInt(headerLength);
        buf.putInt(index);
        buf.put(source.getBytes());      buf.put((byte)0);
        buf.put(sourceId.getBytes());      buf.put((byte)0);
        buf.put(destination.getBytes()); buf.put((byte)0);

        buf.putInt(contentType);
        buf.putInt(sentToLength);

        for (int i = 0; i < sentToLength; i++) {
            buf.put(sentToList.get(i).getBytes()); buf.put((byte)0);
        }

        if (contents != null) buf.put(contents);
        return buf.array();
    }

    void sendTo(List<MeshDevice> devices) {
        ArrayList<MeshDevice> sendTo = new ArrayList<>();
        // Add the devices this one will send the message to the sentToList
        for (MeshDevice device : devices) {
            if (sentToList.contains(device.name) || !device.isConnected
                    || device.name.equals(source)) continue;
            sentToList.add(device.name);
            sendTo.add(device);
        }

        if (sendTo.size() <= 0) return;

        // Send this packet with the new devices included in the sentToList
        byte[] bytes = this.toBytes();
        for (MeshDevice device : sendTo) {
            MeshManager.connectionsClient.sendPayload(device.id, Payload.fromBytes(bytes));
        }
    }
    void sendTo(MeshDevice device, List<MeshDevice> devices) {
        if (sentToList.contains(destination)) return;

        // If we can send it to it's destination then do so.
        if (device != null && device.isConnected && !sentToList.contains(device.name)) {
            sentToList.add(device.name);
            MeshManager.connectionsClient.sendPayload(device.id, Payload.fromBytes(this.toBytes()));
        } else {
            // Send it to everyone, hopefully someone can get it to it's destination
            sendTo(devices);
        }
    }
}
