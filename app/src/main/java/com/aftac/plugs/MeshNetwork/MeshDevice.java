package com.aftac.plugs.MeshNetwork;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;

public class MeshDevice {
    String name;
    String groupName;
    String id;
    long lastPing;
    int serialRx;
    int serialTx;
    boolean newInGroup   = false;
    boolean isAvailable  = false;
    boolean isConnecting = false;
    boolean isConnected  = false;
    boolean isInMesh = false;
    int[] packetsReceived;

    int packetsReceivedPos = 0;

    int groupAddStatus = 0;
    List<MeshDevice> confirmedGroupMembers = new ArrayList<>();

    MeshDevice(String name, String id) {
        this.id = id;
        setName(name);
        serialRx = 0;
        serialTx = 0;
        packetsReceived = new int[4096];
        lastPing = SystemClock.elapsedRealtime();
    }
    public String getName() { return name; }
    public String getGroupName() { return groupName; }
    public boolean isInMesh() { return isInMesh; }
    public boolean isConnected() { return isConnected; }
    public boolean isConnecting() { return isConnecting; }
    public long timeSinceLastPing() { return SystemClock.elapsedRealtime() - lastPing; }

    public void setName(String name) {
        this.name = name;
        int pos = name.lastIndexOf((char)10);
        if (pos >= 0) {
            this.groupName = name.substring(pos + 1);
            this.name = name.substring(0, pos);
        }
    }

    void receivedPacket(int index) {
        packetsReceived[packetsReceivedPos++] = index;
        if (packetsReceivedPos >= packetsReceived.length)
            packetsReceivedPos = 0;
        lastPing = SystemClock.elapsedRealtime();
    }

    boolean hasPacketBeenReceived(int index) {
        for (int i = packetsReceivedPos; i >= 0; i--) {
            if (packetsReceived[i] == index)
                return true;
        }
        for (int i = packetsReceived.length - 1; i > packetsReceivedPos; i--) {
            if (packetsReceived[i] == index)
                return true;
        }
        return false;
    }
}
