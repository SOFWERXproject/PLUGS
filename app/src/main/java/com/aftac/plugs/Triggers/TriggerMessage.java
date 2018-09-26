package com.aftac.plugs.Triggers;

import java.io.Serializable;

public class TriggerMessage implements Serializable {

    // Van Horne: I only created this as a class for Queue to receive as triggers
    // It can be changed as needed later

    long timestamp;
    int  sourceID;
    int  triggerType;
    int  sensorId;
    // TODO: finish TriggerMessage class...

}
