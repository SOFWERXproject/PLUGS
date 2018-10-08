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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static android.os.Looper.getMainLooper;

public class MeshManager {
    private static final String LOG_TAG = MeshManager.class.getSimpleName();

    private static final String SERVICE_ID = "plugs-mesh";

    static final int COMMAND_START_DISCOVERY = 1;
    static final int COMMAND_STOP_DISCOVERY = 2;
    static final int COMMAND_GET_PEERS = 3;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_P2P = 1;
    public static final int ERROR_INIT_P2P_SERVICE = 2;
    public static final int ERROR_LISTEN = 3;

    public static final int STATE_ENABLED_FLAG    = 0x0001;
    public static final int STATE_LISTENING_FLAG  = 0x0002;
    public static final int STATE_CONNECTING_FLAG = 0x0004;
    public static final int STATE_CONNECTED_FLAG  = 0x0008;
    public static final int STATE_ERROR_FLAG      = 0x0080;
    public static final int STATE_ENABLED_CHANGED_FLAG    = 0x0100;
    public static final int STATE_LISTENING_CHANGED_FLAG  = 0x0200;
    public static final int STATE_CONNECTING_CHANGED_FLAG = 0x0400;
    public static final int STATE_CONNECTED_CHANGED_FLAG  = 0x0800;
    public static final int STATE_ERROR_OCCURRED_FLAG     = 0x8000;

    private static MeshManager me;
    private static ConnectionsClient connectionsClient;
    private static Handler mainHandler;
    private static ProgressDialog progressDialog;

    private static final IntentFilter intentFilter = new IntentFilter();

    private static final List<ListenerCallback> peerChangeListeners = new ArrayList<>();
    private static final List<ListenerCallback> statusChangeListeners = new ArrayList<>();
    private static final List<MeshDevice> availableDevices = new ArrayList<>();
    private static final List<MeshDevice> connectedDevices = new ArrayList<>();

    private static boolean hasError = false;
    private static int lastError = ERROR_NONE;
    private static int lastErrorCode = 0;

    private static int activeConnectionRequests = 0;
    private static boolean isListening = false;
    private static boolean isEnabled = false;
    private static boolean isConnected = false;


    private interface MeshListener {}

    public interface MeshPeersChangedListener extends MeshListener {
        void onMeshPeersChanged(List<MeshDevice> devices);
    }
    public interface MeshStatusChangedListener extends MeshListener {
        void onMeshStatusChanged(int statusFlags);
    }


    private MeshManager() {}

    public static void init(Context context) {
        if (me != null) return;
        me = new MeshManager();
        isEnabled = true;

        mainHandler = new Handler(getMainLooper());

        connectionsClient = Nearby.getConnectionsClient(context);
    }

    public static boolean isEnabled() { return isEnabled; }
    public static boolean isListening() { return isListening; }
    public static boolean isConnected() { return isConnected; }


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

    @Queue.addCommand(COMMAND_STOP_DISCOVERY)
    public static void stopPeerDiscovery() {
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        clearAvailableDevices();
        isListening = false;
        onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
    }

    private static final EndpointDiscoveryCallback discoveryCallback =
                new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String deviceId, DiscoveredEndpointInfo info) {
            deviceAddAvailable(deviceId, info.getEndpointName());
            Log.d(LOG_TAG, "Endpoint found: " + deviceId);
        }

        @Override
        public void onEndpointLost(String deviceId) {
            deviceRemoveAvailable(deviceId);
            Log.d(LOG_TAG, "Endpoint lost: " + deviceId);
        }
    };

    public static void connectTo(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null || device.isConnected || device.isConnecting) return;

        deviceConnecting(deviceId);
        connectionsClient.requestConnection(
                    Queue.getName(),
                    deviceId,
                    connectionLifecycleCallback)
            .addOnSuccessListener((e) -> {
                Log.d(LOG_TAG, "Request connection succeeded");
                deviceAddConnected(deviceId);
            })
            .addOnFailureListener((e)-> {
                Log.d(LOG_TAG, "Request connection failed");
                e.printStackTrace();
                deviceNotConnecting(deviceId);
            });
    }

    private static final ConnectionLifecycleCallback connectionLifecycleCallback =
                new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String deviceId, ConnectionInfo info) {
            Log.d(LOG_TAG, "Connection initiated: " + deviceId);
            connectionsClient.acceptConnection(deviceId, payloadCallback);
            if (getDevice(deviceId) == null)
                deviceAddAvailable(deviceId, info.getEndpointName());
            deviceConnecting(deviceId);
        }

        @Override
        public void onConnectionResult(String deviceId, ConnectionResolution result) {
            Log.d(LOG_TAG, "Connection result: " + deviceId);
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    Log.d(LOG_TAG, "Connection ok - " + deviceId);
                    deviceAddConnected(deviceId);
                break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    Log.d(LOG_TAG, "Connection rejected - " + deviceId);
                    deviceNotConnecting(deviceId);
                break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    Log.d(LOG_TAG, "Connection error - " + deviceId);
                    deviceNotConnecting(deviceId);
                break;
            }
        }

        @Override
        public void onDisconnected(String deviceId) {
            Log.d(LOG_TAG, "Disconnected: " + deviceId);
            deviceRemoveConnected(deviceId);
        }
    };

    private static PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(String endpointName, Payload payload) {
            Log.d(LOG_TAG, "Payload received:" + endpointName);
        }

        @Override
        public void onPayloadTransferUpdate(String endpointName, PayloadTransferUpdate payloadTransferUpdate) {

        }
    };

    private static void deviceAddAvailable(String deviceId, String deviceName) {
        MeshDevice device = getDevice(deviceId);
        if (device == null)
            device = new MeshDevice(deviceId, deviceName);
        else if (deviceName != null)
            device.name = deviceName;

        if (!device.isAvailable) {
            device.isAvailable = true;
            if (!device.isConnecting)
                addDevice(device, availableDevices);
            onDevicesChanged();
        }
    }
    private static void deviceRemoveAvailable(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device != null) {
            if (device.isAvailable) {
                device.isAvailable = false;
                if (!device.isConnecting)
                    availableDevices.remove(device);
                onDevicesChanged();
            }
        }
    }
    private static void clearAvailableDevices() {
        for (MeshDevice device : availableDevices) {
            device.isAvailable = false;
            if (!device.isConnecting)
                availableDevices.remove(device);
        }
        onDevicesChanged();
    }

    private static void deviceConnecting(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        device.isConnecting = true;
        onDevicesChanged();
    }
    private static void deviceNotConnecting(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        device.isConnecting = false;
        if (!device.isAvailable)
            availableDevices.remove(device);
        onDevicesChanged();
    }

    private static void deviceAddConnected(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        device.isConnecting = false;
        if (!device.isConnected) {
            device.isConnected = true;
            addDevice(device, connectedDevices);
        }
        onDevicesChanged();
    }

    private static void deviceRemoveConnected(String deviceId) {
        MeshDevice device = getDevice(deviceId);
        if (device == null) return;

        device.isConnecting = false;
        device.isConnected = false;
        connectedDevices.remove(device);
        onDevicesChanged();
    }

    private static MeshDevice getDevice(String deviceId) {
        for (MeshDevice device : connectedDevices) {
            if (deviceId.equals(device.id))
                return device;
        }
        for (MeshDevice device : availableDevices) {
            if (deviceId.equals(device.id))
                return device;
        }
        return null;
    }

    private static void addDevice(MeshDevice device, List<MeshDevice> list) {
        for (MeshDevice lDevice : list) {
            if (lDevice.id.equals(device.id))
                return;
        }
        list.add(device);
    }


    // Generic listener callback class
    class ListenerCallback {
        WeakReference<MeshListener> listenerRef;
        Handler handler;

        ListenerCallback(MeshListener listener, Handler handler) {
            this.listenerRef = new WeakReference<>(listener);
            this.handler = handler;
        }
    }
    private ListenerCallback createListenerCallback(List<ListenerCallback> list,
                                                    MeshListener listener, Handler handler) {
        ListenerCallback callback = new ListenerCallback(listener, handler);
        list.add(callback);
        return callback;
    }
    private static void removeListenerCallback(List<ListenerCallback> list, MeshListener listener) {
        for (ListenerCallback obj : list) {
            if (obj.listenerRef.get().equals(listener)) {
                list.remove(obj);
                break;
            }
        }
    }

    // Peers changed listeners
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener, Handler handler) {
        onDevicesChanged(
                    me.createListenerCallback(peerChangeListeners, listener, handler));
    }
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener) {
        onDevicesChanged(
            me.createListenerCallback(peerChangeListeners, listener,
                        new Handler(Looper.myLooper())));
    }
    public static void removeOnPeersChangedListener(MeshPeersChangedListener listener) {
        removeListenerCallback(peerChangeListeners, listener);
    }
    private static void onDevicesChanged(ListenerCallback listener) {
        boolean exists;

        List<MeshDevice> list = new ArrayList<>(availableDevices);
        for (MeshDevice device : connectedDevices) {
            exists = false;
            for (MeshDevice exDevice : list) {
                if (exDevice.id.equals(device.id)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) list.add(device);
        }
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
    private static void onDevicesChanged() {
        onDevicesChanged(null);
    }

    // Status changed listeners
    public static void addOnStatusChangedListener(MeshStatusChangedListener listener, Handler handler) {
        me.createListenerCallback(statusChangeListeners, listener, handler);
    }
    public static void addOnStatusChangedListener(MeshStatusChangedListener listener){
        me.createListenerCallback(statusChangeListeners, listener, new Handler(Looper.myLooper()));
    }
    public static void removeOnStatusChangedListener(MeshStatusChangedListener listener) {
        removeListenerCallback(statusChangeListeners, listener);
    }
    private static void onStatusChanged(int extraFlags) {
        Log.d(LOG_TAG, "onStatusChanged");
        int statusFlags = extraFlags
                        | (isConnected ? STATE_ENABLED_FLAG   : 0)
                        | (isListening ? STATE_LISTENING_FLAG : 0)
                        | (isConnected ? STATE_CONNECTED_FLAG : 0)
                        | (hasError    ? STATE_ERROR_FLAG     : 0);
        for (ListenerCallback callback : statusChangeListeners) {
            callback.handler.post(() -> ((MeshStatusChangedListener) callback.listenerRef.get())
                        .onMeshStatusChanged(statusFlags));
        }
    }
}