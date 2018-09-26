package com.aftac.plugs.Queue;

import java.io.Serializable;

public class QueueCommand implements Serializable {
    static final int COMMAND_NONE = 0;
    // General                                        0x00XX;

    // Sensors                                        0x01XX;
    static final int COMMAND_SENSOR_GET_LIST        = 0x0100;
    static final int COMMAND_SENSOR_GET_ATTRIBUTES  = 0x0101;

    // Triggers                                       0x02XX;
    static final int COMMAND_TRIGGER_GET_LIST       = 0x0100;
    static final int COMMAND_TRIGGER_GET_ATTRIBUTES = 0x0101;


    static final int TARGET_NONE = 0x0000;
    static final int TARGET_ALL  = 0x7FFF;

    long timestamp;
    int  sourceID;
    int  targetID;
    int  commandType;
    int  command;

    // TODO: allow command arguments
}
