package com.aftac.plugs.MeshNetwork;

import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class MeshDevice {
    String id;
    String name;
    int serialRx;
    int serialTx;
    boolean newInGroup   = false;
    boolean isAvailable  = false;
    boolean isConnecting = false;
    boolean isConnected  = false;

    int groupAddStatus = 0;
    List<MeshDevice> confirmedGroupMembers = new ArrayList<>();

    MeshDevice(String id, String name) {
        this.id = id;
        this.name = name;
        serialRx = 0;
        serialTx = 0;
    }
    public String getId()   { return id; }
    public String getName() { return name; }
    public boolean isConnected() { return isConnected; }
    public boolean isConnecting() { return isConnecting; }
}
