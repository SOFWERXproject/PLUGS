package com.aftac.plugs.Queue;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

public class QueueCommand extends Queue.QueueItem {
    @Override int getType() { return Queue.ITEM_TYPE_COMMAND; }

    String target;
    String source = Queue.getName();
    int commandClass;
    int commandId;
    JSONArray args;
    CommandResponseListener responseListener = null;
    Handler responseHandler = Queue.workHandler;

    public QueueCommand(String target, int commandClass, int commandId, JSONArray arguments) {
        super();
        this.commandClass = commandClass;
        this.target  = target;
        this.commandId = commandId;
        this.args = arguments;
    }

    public QueueCommand(ByteBuffer buffer, boolean fromIntent) {
        super();
        fromBytes(buffer, fromIntent);
    }
    public QueueCommand(ByteBuffer buffer) {
        fromBytes(buffer, false);
    }



    public String getSource() { return source; }
    public String getTarget() { return target; }

    public void fromBytes(ByteBuffer buffer, boolean fromIntent) {
        byte chr;
        try {
            source = ""; while ((chr = buffer.get()) != 0) { source += (char)chr; }
            target = ""; while ((chr = buffer.get()) != 0) { target += (char)chr; }
        } catch (Exception e) { e.printStackTrace(); }

        // If the byte buffer didn't come from an intent reject the source/target being "self"
        String myId = Queue.getName();
        if (!fromIntent && !myId.equals("")
                && (target.equals(Queue.COMMAND_TARGET_SELF) || source.equals(myId))) {
            source = Queue.COMMAND_TARGET_NONE;
            target = Queue.COMMAND_TARGET_NONE;
            return;
        }

        commandClass = buffer.getInt();
        commandId    = buffer.getInt();

        try {
            ByteBuffer sliced = buffer.slice();
            byte[] bytes = new byte[sliced.remaining()];
            sliced.get(bytes);
            String strJSON = new String(bytes, "UTF-8");
            Log.v(Queue.LOG_TAG, "Command parsed: " + source + ", " + target + ", "
                    + commandClass + ", " + commandId);
            Log.v(Queue.LOG_TAG, strJSON);
            args = new JSONArray(strJSON);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public byte[] toBytes() {
        Log.v(Queue.LOG_TAG, "Command packed: " + source + ", " + target + ", "
                + commandClass + ", " + commandId);

        String argStr = args.toString();
        ByteBuffer ret = ByteBuffer.wrap(new byte[8 + source.length() + target.length()
                + argStr.length() + 3]);

        try {
            ret.put(source.getBytes()); ret.put((byte)0);
            ret.put(target.getBytes()); ret.put((byte)0);
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
