package com.aftac.plugs.MeshNetwork;

import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;

import java.nio.ByteBuffer;

public class MeshDevice {
    String id;
    String name;


    MeshDevice(ByteBuffer data) {
        //this.id = data.getString();
        //this.deviceName = data.slice().toString();
    }
    MeshDevice(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public String getName() { return name; }
}
