package com.aftac.plugs.MeshNetwork;

import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;

import java.nio.ByteBuffer;

public class MeshDevice {
    String id;
    String name;
    int status = 0;
    boolean isAvailable  = false;
    boolean isConnecting = false;
    boolean isConnected  = false;

    MeshDevice(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId()   { return id; }
    public String getName() { return name; }
    public boolean isConnected() { return isConnected; }
    public boolean isConnecting() { return isConnecting; }
}
