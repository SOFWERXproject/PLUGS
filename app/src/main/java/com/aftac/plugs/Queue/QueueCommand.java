package com.aftac.plugs.Queue;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class QueueCommand extends Queue.QueueItem {
    @Override int getType() { return Queue.ITEM_TYPE_COMMAND; }

    String targetId;
    String sourceId = Queue.getId();
    int commandClass;
    int commandId;
    JSONArray args;
    CommandResponseListener responseListener = null;
    Handler responseHandler = Queue.workHandler;

    public QueueCommand(String targetId, int commandClass, int commandId, JSONArray arguments) {
        super();
        this.commandClass = commandClass;
        this.targetId  = targetId;
        this.commandId = commandId;
        this.args = arguments;
    }

    public QueueCommand(ByteBuffer buffer) {
        super();
        byte chr;

        try {
            sourceId = ""; while ((chr = buffer.get()) != 0) { sourceId += (char)chr; };
            targetId = ""; while ((chr = buffer.get()) != 0) { targetId += (char)chr; };
        } catch (Exception e) { e.printStackTrace(); }

        // Don't accept byte arrays where source/target = self
        String myId = Queue.getId();
        if (!myId.equals("")
                    && (targetId.equals(Queue.COMMAND_TARGET_SELF) || sourceId.equals(myId))) {
            sourceId = Queue.COMMAND_TARGET_NONE;
            targetId = Queue.COMMAND_TARGET_NONE;
            return;
        }

        commandClass = buffer.getInt();
        commandId    = buffer.getInt();

        try {
            ByteBuffer sliced = buffer.slice();
            byte[] bytes = new byte[sliced.remaining()];
            sliced.get(bytes);
            String strJSON = new String(bytes, "UTF-8");
            Log.v(Queue.LOG_TAG, "Command parsed: " + sourceId + ", " + targetId + ", "
                    + commandClass + ", " + commandId);
            Log.v(Queue.LOG_TAG, strJSON);
            args = new JSONArray(strJSON);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public byte[] toBytes() {
        Log.v(Queue.LOG_TAG, "Command packed: " + sourceId + ", " + targetId + ", "
                + commandClass + ", " + commandId);

        String argStr = args.toString();
        ByteBuffer ret = ByteBuffer.wrap(new byte[8 + sourceId.length() + targetId.length()
                    + argStr.length() + 3]);

        try {
            ret.put(sourceId.getBytes()); ret.put((byte)0);
            ret.put(targetId.getBytes()); ret.put((byte)0);
        } catch (Exception e) { e.printStackTrace(); }
        ret.putInt(commandClass);
        ret.putInt(commandId);
        ret.put(argStr.getBytes()); ret.put((byte)0);

        return ret.array();
    }

    // A handler can be set for the responseListener, or just make one for the calling thread
    public void setResponseListener(CommandResponseListener listener, Handler handler) {
        responseListener = listener;
        responseHandler  = handler;
    }
    public void setResponseListener(CommandResponseListener listener) {
        setResponseListener(listener, new Handler(Looper.myLooper()));
    }
}
