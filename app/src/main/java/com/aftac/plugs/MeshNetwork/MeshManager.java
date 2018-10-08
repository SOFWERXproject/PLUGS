package com.aftac.plugs.MeshNetwork;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aftac.plugs.Queue.Queue;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;

import org.json.JSONArray;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static android.os.Looper.getMainLooper;

public class MeshManager {
    private static final String LOG_TAG = MeshManager.class.getSimpleName();

    private static final String SERVICE_ID = "plugs-mesh";

    public static final int COMMAND_START_DISCOVERY        = 1;
    public static final int COMMAND_STOP_DISCOVERY         = 2;
    public static final int COMMAND_GET_PEERS              = 3;
    public static final int COMMAND_ADD_TO_MESH_GROUP      = 4;
    public static final int COMMAND_REMOVE_FROM_MESH_GROUP = 5;

    public static final int CONTENT_HELLO      = 0x00;
    public static final int CONTENT_COMMENT    = 0x01;
    public static final int CONTENT_NTP        = 0x02;
    public static final int CONTENT_CONFIRM    = 0x03;
    public static final int CONTENT_DENY       = 0x04;
    public static final int CONTENT_MESH_INFO  = 0x05;

    public static final int CONTENT_COMMAND    = 0x100;
    public static final int CONTENT_TRIGGER    = 0x101;

    public static final String DEVICE_SELF = "%SELF%";
    public static final String DEVICE_ALL  = "%ALL%";

    public static final int STATE_ENABLED_FLAG    = 0x0001;
    public static final int STATE_LISTENING_FLAG  = 0x0002;
    public static final int STATE_CONNECTING_FLAG = 0x0004;
    public static final int STATE_CONNECTED_FLAG  = 0x0008;
    public static final int STATE_DEVICE_ID_FLAG  = 0x0010;
    public static final int STATE_ERROR_FLAG      = 0x0080;
    public static final int STATE_ENABLED_CHANGED_FLAG    = 0x0100;
    public static final int STATE_LISTENING_CHANGED_FLAG  = 0x0200;
    public static final int STATE_CONNECTING_CHANGED_FLAG = 0x0400;
    public static final int STATE_CONNECTED_CHANGED_FLAG  = 0x0800;
    public static final int STATE_DEVICE_ID_CHANGED_FLAG  = 0x1000;
    public static final int STATE_ERROR_OCCURRED_FLAG     = 0x8000;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_P2P = 1;
    public static final int ERROR_INIT_P2P_SERVICE = 2;
    public static final int ERROR_LISTEN = 3;

    private static MeshManager me;
    private static ConnectionsClient connectionsClient;

    private static final IntentFilter intentFilter = new IntentFilter();

    private static final List<ListenerCallback> peerChangeListeners = new ArrayList<>();
    private static final List<ListenerCallback> statusChangeListeners = new ArrayList<>();
    private static final List<MeshDevice> availableDevices = new ArrayList<>();
    private static final List<MeshDevice> connectedDevices = new ArrayList<>();

    private static final List<MeshDevice> myMeshGroup = new ArrayList<>();

    private static boolean hasError = false;
    private static int lastError = ERROR_NONE;
    private static int lastErrorCode = 0;
    private static String myDeviceId;

    private static boolean isListening = false;
    private static boolean isEnabled = false;
    private static boolean isConnected = false;


    // Listener interfaces
    private interface MeshListener {}
    public interface MeshPeersChangedListener extends MeshListener {
        void onMeshPeersChanged(List<MeshDevice> devices);
    }
    public interface MeshStatusChangedListener extends MeshListener {
        void onMeshStatusChanged(int statusFlags);
    }

    // Nothing should need to instantiate this class except itself
    private MeshManager() {}

    public static void init(Context context) {
        // Only initialize once
        if (isEnabled) return;
        isEnabled = true;

        // Instance for non-static methods (all of which should be private)
        me = new MeshManager();

        // Nearby Connections API client
        connectionsClient = Nearby.getConnectionsClient(context);
    }

    // Getters
    public static boolean isEnabled()    { return isEnabled;   }
    public static boolean isListening()  { return isListening; }
    public static boolean isConnected()  { return isConnected; }
    public static String getMyDeviceId() { return myDeviceId;  }

    // Starts advertising & searching for peers
    @Queue.addCommand(COMMAND_START_DISCOVERY)
    public static void startPeerDiscovery() {
        connectionsClient.startAdvertising(
                    Queue.getName(),
                    SERVICE_ID,
                    connectionLifecycleCallback,
                    new AdvertisingOptions(Strategy.P2P_CLUSTER))
            .addOnSuccessListener((aVoid) -> {
                Log.d(LOG_TAG, "Start advertising succeeded");
                if (!isListening) {
                    isListening = true;
                    onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
                }
            })
            .addOnFailureListener((e) -> {
                Log.d(LOG_TAG, "Start advertising failed");
                e.printStackTrace();
            });

        connectionsClient.startDiscovery(
                    SERVICE_ID,
                    discoveryCallback,
                    new DiscoveryOptions(Strategy.P2P_CLUSTER))
            .addOnSuccessListener((unusedResult) -> {
                Log.d(LOG_TAG, "Start Discovery succeeded");
                if (!isListening) {
                    isListening = true;
                    onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
                }
            })
            .addOnFailureListener((e) -> {
                Log.d(LOG_TAG, "Start Discovery failed");
                e.printStackTrace();
            });
    }

    // Stops advertising & searching for peers
    @Queue.addCommand(COMMAND_STOP_DISCOVERY)
    public static void stopPeerDiscovery() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();

        // Clear the list of available peers
        clearAvailableDevices();

        isListening = false;
        onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
    }

    @Queue.addCommand(COMMAND_ADD_TO_MESH_GROUP)
    public static void addToMeshGroup(String deviceId, String commandSource) {
        // TODO: addToMeshGroup
        MeshDevice device = getDevice(deviceId);

        // Add device to mesh group and connect
        if (device != null) {
            if (!inMeshGroup(deviceId)) {
                myMeshGroup.add(device);
                device.newInGroup = true;
                if (device.isConnected) {
                    // Send current mesh group members to new member
                    if (commandSource == DEVICE_SELF)
                        connectToMeshGroup(deviceId);
                } else
                    connectTo(deviceId);
            } else
                return;
        } else {
            device = new MeshDevice(deviceId, "");
            device.newInGroup = true;
            myMeshGroup.add(device);
        }

        if (commandSource == DEVICE_SELF) {

        }


        // The following assumes source is DEVICE_SELF
        // Is device available?
            // Yes: Attempt to connect to device
            // No: Abort, return error message

        // Does device have it's own mesh group?
        // Yes
            // Does the user want to merge mesh groups?
                // Yes: Add all members of new device's mesh group to this device's mesh group
                // No: Abort operation
        // No
            // Add the new device to this device's mesh group

        // Inform the new device of all the members currently in the group
        // Announce the addition of the  new device to the members currently in the group

        // Wait for confirmations & retry as necessary
    }
    public static void addToMeshGroup(String deviceId) {
        addToMeshGroup(deviceId, DEVICE_SELF);
    }

    private static void connectToMeshGroup(String deviceId) {
        for (MeshDevice addDevice : myMeshGroup) {
            if (deviceId.equals(addDevice.id))
                continue;

            Log.v(LOG_TAG, "Connecting " + deviceId + " to " + addDevice.id);

            Object[] args = {addDevice.id, myDeviceId};
            Queue.Command command = new Queue.Command(
                    Queue.COMMAND_TARGET_SELF,
                    Queue.COMMAND_CLASS_MESH_NET,
                    MeshManager.COMMAND_ADD_TO_MESH_GROUP,
                    new JSONArray(Arrays.asList(args)));
            sendBytes(deviceId, command.toBytes());
        }
    }

    @Queue.addCommand(COMMAND_REMOVE_FROM_MESH_GROUP)
    public static void removeFromMeshGroup(String deviceId, String commandSource) {
        // TODO: removeFromMeshGroup

        // The following assumes source is DEVICE_SELF
        // Say goodbye to the device
        // Tell the rest of the members of the mesh group the device is gone now
        // Wait for confirmations & retry as necessary
    }
    public static void removeFromMeshGroup(String deviceId) {
        removeFromMeshGroup(deviceId, DEVICE_SELF);
    }

    // Callback for when peers are discovered
    private static final EndpointDiscoveryCallback discoveryCallback =
                new EndpointDiscoveryCallback() {
        // Adds a new peer to the available list
        @Override
        public void onEndpointFound(String deviceId, DiscoveredEndpointInfo info) {
            deviceAddAvailable(deviceId, info.getEndpointName());
            if (inMeshGroup(deviceId))
                connectTo(deviceId);

            Log.d(LOG_TAG, "Endpoint found: " + deviceId);
        }

        // Removes a peer from the available list
        @Override
        public void onEndpointLost(String deviceId) {
            deviceRemoveAvailable(deviceId);
            Log.d(LOG_TAG, "Endpoint lost: " + deviceId);
        }
    };

    // Starts connection to a peer
    public static void connectTo(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        // Ignore devices that don't exist, and ones that already are or are currently connecting.
        if (device == null || device.isConnected || device.isConnecting) return;

        // Send the connection request
        deviceConnecting(deviceId);
        connectionsClient.requestConnection(
                    Queue.getName(),
                    deviceId,
                    connectionLifecycleCallback)
            .addOnSuccessListener((e) -> {
                Log.d(LOG_TAG, "Request connection succeeded");
                // This doesn't confirm the connection was accepted just that the request was
                // successfully sent
            })
            .addOnFailureListener((e)-> {
                Log.d(LOG_TAG, "Request connection failed");
                e.printStackTrace();
                deviceNotConnecting(deviceId);
            });
    }

    // Handles lifecyle of connections
    private static final ConnectionLifecycleCallback connectionLifecycleCallback =
                new ConnectionLifecycleCallback() {
        // Handles initiation (both locally & remotely requested) of a connection
        @Override
        public void onConnectionInitiated(String deviceId, ConnectionInfo info) {
            Log.d(LOG_TAG, "Connection initiated: " + deviceId);

            // TODO: This is where an "Are you sure you want to connect" dialog would be shown

            connectionsClient.acceptConnection(deviceId, payloadCallback);
            // This device isn't in the available list, add it.
            if (getDevice(deviceId) == null)
                deviceAddAvailable(deviceId, info.getEndpointName());

            // Set this deviceId's status to connecting
            deviceConnecting(deviceId);
        }

        // Determines if connection was successful or not, and reacts accordingly
        @Override
        public void onConnectionResult(String deviceId, ConnectionResolution result) {
            Log.d(LOG_TAG, "Connection result: " + deviceId);
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    Log.d(LOG_TAG, "Connection ok - " + deviceId);
                    deviceAddConnected(deviceId);
                    int headerLength = 12 + deviceId.length() + 1;

                    if (!inMeshGroup(deviceId))
                        addToMeshGroup(deviceId);

                    MeshDevice device = getDevice(deviceId);

                    ByteBuffer buf = ByteBuffer.wrap(new byte[headerLength]);
                    buf.putInt(headerLength);
                    buf.putInt(device.serialTx++);
                    buf.putInt(CONTENT_HELLO);
                    buf.put(deviceId.getBytes());
                    buf.put((byte)0);

                    if (device.newInGroup) {
                        device.newInGroup = false;
                        connectToMeshGroup(deviceId);
                    }

                    connectionsClient.sendPayload(deviceId, Payload.fromBytes(buf.array()));
                break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    Log.d(LOG_TAG, "Connection rejected - " + deviceId);
                    deviceNotConnecting(deviceId);
                break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    Log.d(LOG_TAG, "Connection error - " + deviceId);
                    deviceNotConnecting(deviceId);
                break;
                default:
                    // If the connection status wasn't "OK", assume it failed.
                    deviceNotConnecting(deviceId);
                break;
            }
        }

        // Removes disconnected devices
        @Override
        public void onDisconnected(String deviceId) {
            Log.d(LOG_TAG, "Disconnected: " + deviceId);
            deviceRemoveConnected(deviceId);
        }
    };

    // Reacts to received payloads, and tracks upload/transmission progress
    private static PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String deviceId, Payload payload) {
            Log.d(LOG_TAG, "Payload received:" + deviceId);
            // TODO: handle payloads
            MeshDevice device = getDevice(deviceId);

            ByteBuffer buf = ByteBuffer.wrap(payload.asBytes());

            byte chr;
            int headerLength = buf.getInt();
            int serial       = buf.getInt();
            int contentType  = buf.getInt();

            String destId = "";
            while ((chr = buf.get()) != 0) { destId += (char)chr; }

            String srcId = "";
            if (contentType != CONTENT_HELLO) {
                while ((chr = buf.get()) !=0){ srcId += (char)chr; }
            }

            if (serial != device.serialRx++) {
                Log.v(LOG_TAG, "WARNING " + deviceId + ": Serial value jump ("
                            + device.serialRx + ", " + serial + ")");
                device.serialRx = serial++;
            } else
                device.serialRx++;

            /*
                Header
                    int     headerLength
                    int     serial
                    int     contentType
                            sourceDeviceId
                            destinationDeviceId

             */
            switch (contentType) {
                case CONTENT_HELLO:
                    if (myDeviceId != null && !myDeviceId.equals(destId)) {
                        Log.v(LOG_TAG, "WARNING " + deviceId + ": device ID mismatch ("
                                    + myDeviceId + ", " + destId + ")");
                    }
                    myDeviceId = destId;
                    onStatusChanged(STATE_DEVICE_ID_CHANGED_FLAG);
                break;
                case CONTENT_COMMENT:
                    // Ignore
                break;

                case CONTENT_NTP:
                    // Negotiate mesh time
                break;

                case CONTENT_CONFIRM:
                break;

                case CONTENT_DENY:
                break;

                case CONTENT_MESH_INFO:
                    // Exchange names, id's, introduce devices, negotiate settings, e.t.c.
                break;


                case CONTENT_COMMAND:
                    Queue.Command command = new Queue.Command(buf.slice());
                    Queue.push(command);
                break;
                case CONTENT_TRIGGER:
                    //Queue.Trigger trigger = new Queue.Trigger(buf.slice());
                    //Queue.push(trigger);
                break;
                //case CONTENT_COMMAND_RESPONSE:
                //break;


                // case CONTENT_MESH_INFO:
                // case CONTENT_OTHER:
            }
        }

        @Override
        public void onPayloadTransferUpdate(String endpointName, PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    // TODO: Confirmations? Redundancy for mesh group?
    public static void sendBytes(List<String> destinationIds, byte[] inBuf) {

        //ByteBuffer buf = ByteBuffer.allocate(inBuf.length + header.length);
        //connectionsClient.sendPayload(destinationIds, payload);
    }
    public static void sendFile(List<String> destinationIds, File file) {
        //Payload payload = Payload.fromFile(file);
    }
    public static void sendBytes(String destinationId, byte[] inBuf) {
        if (destinationId == DEVICE_ALL) {
            // TODO: Send payload to all in mesh group
        } else {
            MeshDevice device = getDevice(destinationId);
            int headerLength = 12 + destinationId.length() + myDeviceId.length() + 2;
            ByteBuffer buf = ByteBuffer.wrap(
                        new byte[inBuf.length + headerLength]);
            buf.putInt(headerLength);
            buf.putInt(device.serialTx++);
            buf.putInt(CONTENT_COMMAND);
            buf.put(destinationId.getBytes());
            buf.put((byte)0);
            buf.put(myDeviceId.getBytes());
            buf.put((byte)0);
            buf.put(inBuf);

            connectionsClient.sendPayload(destinationId, Payload.fromBytes(buf.array()));
        }
    }

    private static boolean inMeshGroup(String deviceId) {
        for (MeshDevice device : myMeshGroup) {
            if (deviceId.equals(device.id))
                return true;
        }
        return false;
    }

    // Adds a peer to the available devices list
    private static void deviceAddAvailable(String deviceId, String deviceName) {
        // If an instance already exists for this deviceId reuse it, otherwise create a new one
        MeshDevice device = getDevice(deviceId);
        if (device == null)
            device = new MeshDevice(deviceId, deviceName);
        else if (deviceName != null)
            device.name = deviceName; // Update name on recycled deviceId

        // Try to prevent unnecessary onDeviceChanged events, and accidental duplicate devices
        if (!device.isAvailable) {
            device.isAvailable = true;
            if (!device.isConnecting)
                addDevice(device, availableDevices);
            onDevicesChanged();
        }
    }
    // Removes a peer from the available devices list
    private static void deviceRemoveAvailable(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        // If the device doesn't exist our work here is done
        if (device == null) return;

        // Try to prevent unnecessary onDeviceChanged events
        if (device.isAvailable) {
            device.isAvailable = false;
            // Don't completely remove devices that are trying to connect
            if (!device.isConnecting)
                availableDevices.remove(device);
            onDevicesChanged();
        }
    }
    // Clears out the list of available devices
    private static void clearAvailableDevices() {
        int length = availableDevices.size();
        MeshDevice device;
        // Iterate in reverse so unchecked items don't shift when items are removed from the list
        for (int i = length - 1; i >= 0; i--) {
            device = availableDevices.get(i);
            device.isAvailable = false;
            // Keep devices that are trying to connect
            if (!device.isConnecting)
                availableDevices.remove(device);
        }
        // It's probably safe to assume something changed, if not no harm done.
        onDevicesChanged();
    }

    // Sets a device's status to connecting
    private static void deviceConnecting(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        // A non-existent device can't connect...
        if (device == null) return;

        device.isConnecting = true;
        onDevicesChanged();
    }
    // Clears the connecting status from a device
    private static void deviceNotConnecting(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        // Ignore non-existent devices...
        if (device == null) return;

        device.isConnecting = false;
        // If the device isn't in the available list any more get rid of it
        if (!device.isAvailable)
            availableDevices.remove(device);
        onDevicesChanged();
    }

    // Adds a device to the connected devices list
    private static void deviceAddConnected(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        // Don't add devices to the connected list if they're already connected
        if (!device.isConnected) {
            device.isConnected = true;
            addDevice(device, connectedDevices);
        }

        // The device isn't just trying to connect any more
        device.isConnecting = false;
        onDevicesChanged();
    }

    // Removes a device from the connected list
    private static void deviceRemoveConnected(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        // Not connecting, not connected, it's just not.
        device.isConnecting = false;
        device.isConnected = false;
        connectedDevices.remove(device);
        onDevicesChanged();
    }

    // Searches for a MeshDevice with a given device Id
    private static MeshDevice getDevice(String deviceId) {
        for (MeshDevice device : myMeshGroup) {
            if (deviceId.equals(device.id))
                return device;
        }
        // Check for a device with the same deviceId in the connected devices
        for (MeshDevice device : connectedDevices) {
            if (deviceId.equals(device.id))
                return device;
        }
        // Check for a device with the same deviceId in the available devices
        for (MeshDevice device : availableDevices) {
            if (deviceId.equals(device.id))
                return device;
        }
        // No device found
        return null;
    }

    // Adds a device to a list
    // This is basically just an extra layer of protection against duplicate devices
    private static void addDevice(MeshDevice device, List<MeshDevice> list) {
        for (MeshDevice lDevice : list) {
            if (lDevice.id.equals(device.id))
                return;
        }
        list.add(device);
    }


    // Generic listener callback class to decrease amount of code needed for listeners
    // Also implements WeakReferences to try to prevent memory leaks when listeners forget to
    // remove themselves when they're done.
    class ListenerCallback {
        WeakReference<MeshListener> listenerRef;
        Handler handler;

        ListenerCallback(MeshListener listener, Handler handler) {
            this.listenerRef = new WeakReference<>(listener);
            this.handler = handler;
        }
    }
    // This setup has an added benefit of hiding a non-static method inside of MethodManager
    // Create a generic listener callback
    private ListenerCallback createListenerCallback(List<ListenerCallback> list,
                                                    MeshListener listener, Handler handler) {
        ListenerCallback callback = new ListenerCallback(listener, handler);
        list.add(callback);
        return callback;
    }
    // Remove a generic listener callback
    private static void removeListenerCallback(List<ListenerCallback> list, MeshListener listener) {
        for (ListenerCallback obj : list) {
            if (obj.listenerRef.get().equals(listener)) {
                list.remove(obj);
                break;
            }
        }
    }

    // Adds listener for peer changes with/without custom thread handler
    // Also send the list as it currently is when a callback is first connected
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener, Handler handler) {
        onDevicesChanged(
                    me.createListenerCallback(peerChangeListeners, listener, handler));
    }
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener) {
        onDevicesChanged(
            me.createListenerCallback(peerChangeListeners, listener,
                        new Handler(Looper.myLooper())));
    }
    // Remove a peer change listener
    public static void removeOnPeersChangedListener(MeshPeersChangedListener listener) {
        removeListenerCallback(peerChangeListeners, listener);
    }
    // Fires off the peer/device changed callbacks
    private static void onDevicesChanged(ListenerCallback listener) {
        // Create a duplicate list of the available devices
        List<MeshDevice> list = new ArrayList<>(availableDevices);
        boolean exists;

        // Merge in the connected device list skipping any that are also in the available list
        for (MeshDevice device : connectedDevices) {
            exists = false;
            for (MeshDevice checkDevice : availableDevices) {
                if (device.id.equals(checkDevice.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) list.add(device);
        }

        // Send the list out to the listener(s)
        if (listener != null) {
            listener.handler.post(() -> ((MeshPeersChangedListener) listener.listenerRef.get())
                    .onMeshPeersChanged(list));
        } else {
            for (ListenerCallback callback : peerChangeListeners) {
                callback.handler.post(() -> ((MeshPeersChangedListener) callback.listenerRef.get())
                            .onMeshPeersChanged(list));
            }
        }
    }
    // Helper for when a specific listener is not given
    private static void onDevicesChanged() {
        onDevicesChanged(null);
    }

    // Adds a mesh network status change listener with/without custom thread handler
    public static void addOnStatusChangedListener(MeshStatusChangedListener listener, Handler handler) {
        me.createListenerCallback(statusChangeListeners, listener, handler);
    }
    public static void addOnStatusChangedListener(MeshStatusChangedListener listener){
        me.createListenerCallback(statusChangeListeners, listener, new Handler(Looper.myLooper()));
    }
    // Removes a mesh network status change listener
    public static void removeOnStatusChangedListener(MeshStatusChangedListener listener) {
        removeListenerCallback(statusChangeListeners, listener);
    }
    // Fires off mesh network status change callbacks
    private static void onStatusChanged(int extraFlags) {
        Log.d(LOG_TAG, "onStatusChanged");
        int statusFlags = extraFlags
                        | (isConnected        ? STATE_ENABLED_FLAG   : 0)
                        | (isListening        ? STATE_LISTENING_FLAG : 0)
                        | (isConnected        ? STATE_CONNECTED_FLAG : 0)
                        | (myDeviceId != null ? STATE_DEVICE_ID_FLAG : 0)
                        | (hasError           ? STATE_ERROR_FLAG     : 0);
        for (ListenerCallback callback : statusChangeListeners) {
            callback.handler.post(() -> ((MeshStatusChangedListener) callback.listenerRef.get())
                        .onMeshStatusChanged(statusFlags));
        }
    }
}