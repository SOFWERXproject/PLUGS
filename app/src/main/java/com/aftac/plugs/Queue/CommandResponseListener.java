package com.aftac.plugs.Queue;

public interface CommandResponseListener {
    void onCommandResponse(Object response, QueueCommand command);
}
