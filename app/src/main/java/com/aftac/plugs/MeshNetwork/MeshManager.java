package com.aftac.plugs.MeshNetwork;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static android.os.Looper.getMainLooper;

public class MeshManager {
    static final String LOG_TAG = MeshManager.class.getSimpleName();

    static final String SERVICE_ID = "plugs-mesh";

    static final int COMMAND_START_DISCOVERY = 1;
    static final int COMMAND_STOP_DISCOVERY = 2;
    static final int COMMAND_GET_PEERS = 3;

    public static final int ERROR_NONE = 0;
    public static final int ERROR_NO_P2P = 1;
    public static final int ERROR_INIT_P2P_SERVICE = 2;
    public static final int ERROR_LISTEN = 3;

    public static final int STATE_ENABLED_FLAG   = 0x0001;
    public static final int STATE_LISTENING_FLAG = 0x0002;
    public static final int STATE_CONNECTED_FLAG = 0x0004;
    public static final int STATE_ERROR_FLAG     = 0x0080;
    public static final int STATE_ENABLED_CHANGED_FLAG   = 0x0100;
    public static final int STATE_LISTENING_CHANGED_FLAG = 0x0200;
    public static final int STATE_CONNECTED_CHANGED_FLAG = 0x0400;
    public static final int STATE_DEVICE_CHANGED_FLAG    = 0x0800;
    public static final int STATE_ERROR_OCCURRED_FLAG    = 0x8000;

    private static MeshManager me;
    private static ConnectionsClient connectionsClient;
    private static WifiP2pManager p2pManager;
    private static WifiManager wifiManager;
    private static WifiP2pDevice device;
    private static WifiP2pInfo info;
    private static InetAddress iNetAddr;
    private static Handler mainHandler;
    private static ProgressDialog progressDialog;
    private static ServerSocket listenerSocket;
    private static ArrayList<Socket> sockets;

    private static final IntentFilter intentFilter = new IntentFilter();
    private static WifiP2pManager.Channel channel;

    private static final List<ListenerCallback> peerChangeListeners = new ArrayList<>();
    private static final List<ListenerCallback> statusChangeListeners = new ArrayList<>();
    private static final List<MeshDevice> availableDevices = new ArrayList<>();
    private static final List<MeshDevice> connectedDevices = new ArrayList<>();
    private static WifiP2pDevice thisDevice;

    private static boolean hasError = false;
    private static int lastError = ERROR_NONE;
    private static int lastErrorCode = 0;

    private static boolean wifiEnabled = false;
    private static boolean isListening = false;
    private static boolean isEnabled = false;
    private static boolean isConnected = false;


    private interface MeshListener {};

    public interface MeshPeersChangedListener extends MeshListener {
        void onMeshPeersChanged(List<MeshDevice> available, List<MeshDevice> connected);
    }
    public interface MeshStatusChangedListener extends MeshListener {
        void onMeshStatusChanged(int statusFlags);
    }


    private MeshManager() {}

    public static void init(Context context) {
        if (me != null) {
            if (!isEnabled) {
                //context.registerReceiver(receiver, intentFilter);
                isEnabled = true;
                onStatusChanged(STATE_ENABLED_CHANGED_FLAG);
            }
            return;
        }
        me = new MeshManager();

        mainHandler = new Handler(getMainLooper());

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiEnabled = wifiManager.isWifiEnabled();
        if (!wifiEnabled)
            wifiManager.setWifiEnabled(true);

        connectionsClient = Nearby.getConnectionsClient(context);

        /*p2pManager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        if (p2pManager != null) {
            channel = p2pManager.initialize(context, getMainLooper(), channelListener);
            context.registerReceiver(receiver, intentFilter);
            isEnabled = true;
            onStatusChanged(STATE_ENABLED_CHANGED_FLAG);
        } else {
            hasError = true;
            lastError = ERROR_INIT_P2P_SERVICE;
        }*/
    }

    public static void deInit(Context context) {
        if (isListening) stopPeerDiscovery();
        //context.unregisterReceiver(receiver);
        isEnabled = false;
        onStatusChanged(STATE_ENABLED_CHANGED_FLAG);
    }

    public static boolean isEnabled() { return isEnabled; }
    public static boolean isListening() { return isListening; }
    public static boolean isConnected() { return isConnected; }


    @Queue.addCommand(COMMAND_START_DISCOVERY)
    public static void startPeerDiscovery() {
        /*p2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                isListening = true;
                onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
            }

            @Override
            public void onFailure(int reasonCode) {
                isListening = false;
                hasError = true;
                lastError = ERROR_LISTEN;
                lastErrorCode = reasonCode;
                onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
            }
        });*/
        availableDevices.clear();
        onDevicesChanged();

        connectionsClient.startAdvertising(
                    Queue.getName(),
                    SERVICE_ID,
                    connectionLifecycleCallback,
                    new AdvertisingOptions(Strategy.P2P_CLUSTER))
                .addOnSuccessListener(
                    new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(LOG_TAG, "Start advertising succeeded");
                            isListening |= true;
                        }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(LOG_TAG, "Start advertising failed");
                            e.printStackTrace();
                        }
                    });

        connectionsClient.startDiscovery(
                    SERVICE_ID,
                    discoveryCallback,
                    new DiscoveryOptions(Strategy.P2P_CLUSTER))
                .addOnSuccessListener(
                    new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess (Void unusedResult){
                            Log.d(LOG_TAG, "Start Discovery succeeded");
                            isListening |= true;
                            onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
                        }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(LOG_TAG, "Start Discovery failed");
                            e.printStackTrace();
                        }
                    });
    }

    @Queue.addCommand(COMMAND_STOP_DISCOVERY)
    public static void stopPeerDiscovery() {
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            p2pManager.stopPeerDiscovery(channel, new ActionListener() {
                @Override
                public void onSuccess() {
                    isListening = false;
                    onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
                }

                @Override
                public void onFailure(int reasonCode) {
                    //error(ERROR_STOP_LISTEN, reasonCode);
                }
            });
        } else {
            //TODO: API 15 compat
        }*/
        connectionsClient.stopAdvertising();
        connectionsClient.stopDiscovery();
        isListening = false;
        onStatusChanged(STATE_LISTENING_CHANGED_FLAG);
    }

    private static final EndpointDiscoveryCallback discoveryCallback =
                new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(String deviceId, DiscoveredEndpointInfo info) {
            addAvailableDevice(deviceId, info.getEndpointName());
            Log.d(LOG_TAG, "Endpoint found: " + deviceId);
        }

        @Override
        public void onEndpointLost(String deviceId) {
            if (!removeAvailableDevice(deviceId))
                removeConnectedDevice(deviceId);
            Log.d(LOG_TAG, "Endpoint lost: " + deviceId);
        }
    };

    public static void connectTo(String deviceId) { /*connectTo(MeshPeer peer) {
        if (isConnected) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = peer.device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;
        p2pManager.connect(channel, config, new ActionListener() {

            @Override
            public void onSuccess() {
                isConnected = true;
                onStatusChanged(STATE_CONNECTED_CHANGED_FLAG);
            }

            @Override
            public void onFailure(int reason) {
                isConnected = false;
                onStatusChanged(STATE_CONNECTED_CHANGED_FLAG);
            }
        });*/

        connectionsClient.requestConnection(
                    Queue.getName(),
                    deviceId,
                    connectionLifecycleCallback)
                .addOnSuccessListener(
                    new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d(LOG_TAG, "Request connection succeeded");
                        }
                    })
                .addOnFailureListener(
                    new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(LOG_TAG, "Request connection failed");
                            e.printStackTrace();
                        }
                    });
    }

    private static final ConnectionLifecycleCallback connectionLifecycleCallback =
                new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(String deviceId, ConnectionInfo info) {
            Log.d(LOG_TAG, "Connection initiated: " + deviceId);
            connectionsClient.acceptConnection(deviceId, payloadCallback);
            addConnectedDevice(deviceId, info.getEndpointName());
        }

        @Override
        public void onConnectionResult(String deviceId, ConnectionResolution result) {
            Log.d(LOG_TAG, "Connection result: " + deviceId);
            switch (result.getStatus().getStatusCode()) {
                case ConnectionsStatusCodes.STATUS_OK:
                    Log.d(LOG_TAG, "Connection ok - " + deviceId);
                    //addConnectedDevice(deviceId);
                break;
                case ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED:
                    Log.d(LOG_TAG, "Connection rejected - " + deviceId);
                break;
                case ConnectionsStatusCodes.STATUS_ERROR:
                    Log.d(LOG_TAG, "Connection error - " + deviceId);
                break;
            }
        }

        @Override
        public void onDisconnected(String deviceId) {
            Log.d(LOG_TAG, "Disconnected: " + deviceId);
            removeConnectedDevice(deviceId);
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

    private static void addAvailableDevice(String deviceId, String deviceName) {
        if (isConnected(deviceId)) return;
        if (isAvailable(deviceId)) return;

        boolean foundDev = false;
        for (MeshDevice device : connectedDevices) {
            if (deviceId.equals(device.getId())) {
                foundDev = true;
                break;
            }
        }
        if (!foundDev)
            availableDevices.add(new MeshDevice(deviceId, deviceName));
        onDevicesChanged();
    }
    private static boolean removeAvailableDevice(String deviceId) {
        for (MeshDevice device : availableDevices) {
            if (deviceId.equals(device.id)) {
                availableDevices.remove(device);
                onDevicesChanged();
                return true;
            }
        }
        return false;
    }

    private static void addConnectedDevice(String deviceId, String deviceName) {
        if (isConnected(deviceId)) return;

        MeshDevice foundDev = null;
        for (MeshDevice device : availableDevices) {
            if (deviceId.equals(device.id)) {
                foundDev = device;
                break;
            }
        }

        if (foundDev != null) {
            connectedDevices.add(foundDev);
            availableDevices.remove(foundDev);
        } else
            connectedDevices.add(new MeshDevice(deviceId, deviceName));
        onDevicesChanged();

        if (!isConnected) {
            isConnected = true;
            onStatusChanged(STATE_CONNECTED_CHANGED_FLAG);
        }
    }
    private static boolean removeConnectedDevice(String deviceId) {
        for (MeshDevice device : connectedDevices) {
            if (deviceId.equals(device.id)) {
                connectedDevices.remove(device);
                onDevicesChanged();
                if (isConnected && connectedDevices.size() <= 0) {
                    isConnected = false;
                    onStatusChanged(STATE_CONNECTED_CHANGED_FLAG);
                }
                return true;
            }
        }
        return false;
    }

    private static boolean isConnected(String deviceId) {
        for (MeshDevice device : connectedDevices) {
            if (deviceId.equals(device.id));
                return true;
        }
        return false;
    }
    private static boolean isAvailable(String deviceId) {
        for (MeshDevice device : availableDevices) {
            if (deviceId.equals(device.id));
            return true;
        }
        return false;
    }


    // Generic listener callback class
    class ListenerCallback {
        WeakReference<MeshListener> listenerRef;
        Handler handler;

        ListenerCallback(MeshListener listener, Handler handler) {
            this.listenerRef = new WeakReference(listener);
            this.handler = handler;
        }
    }
    private void createListenerCallback(List<ListenerCallback> list, MeshListener listener, Handler handler) {
        synchronized (list) {
            list.add(new ListenerCallback(listener, handler));
        }
    }
    private static void removeListenerCallback(List<ListenerCallback> list, MeshListener listener) {
        synchronized (list) {
            for (ListenerCallback obj : list) {
                if (obj.listenerRef.get().equals(listener)) {
                    list.remove(obj);
                    break;
                }
            }
        }
    }

    // Peers changed listeners
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener, Handler handler) {
        me.createListenerCallback(peerChangeListeners, listener, handler);
    }
    public static void addOnPeersChangedListener(MeshPeersChangedListener listener) {
        me.createListenerCallback(peerChangeListeners, listener, new Handler(Looper.myLooper()));
    }
    public static void removeOnPeersChangedListener(MeshPeersChangedListener listener) {
        removeListenerCallback(peerChangeListeners, listener);
    }
    public static void onDevicesChanged() {
        List<MeshDevice> list1 = new ArrayList<>();
        List<MeshDevice> list2 = new ArrayList<>();
        synchronized (availableDevices) {
            list1.addAll(availableDevices);
        }
        synchronized (connectedDevices) {
            list2.addAll(connectedDevices);
        }
        for (ListenerCallback listener : peerChangeListeners) {
            listener.handler.post(() -> {
                ((MeshPeersChangedListener)listener.listenerRef.get())
                            .onMeshPeersChanged(list1, list2);
            });
        }
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
    public static void onStatusChanged(int extraFlags) {
        Log.d(LOG_TAG, "onStatusChanged");
        int statusFlags = extraFlags
                        | (isConnected ? STATE_ENABLED_FLAG   : 0)
                        | (isListening ? STATE_LISTENING_FLAG : 0)
                        | (isConnected ? STATE_CONNECTED_FLAG : 0)
                        | (hasError    ? STATE_ERROR_FLAG     : 0);

        for (ListenerCallback listener : statusChangeListeners) {
            listener.handler.post(() -> {
                ((MeshStatusChangedListener)listener.listenerRef.get())
                        .onMeshStatusChanged(statusFlags);
            });
        }
    }


    /*private static final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                // UI update to indicate wifi p2p status.
                int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
                isEnabled = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED);
                Log.d(LOG_TAG, "P2P state changed - " + state);
                onStatusChanged(STATE_ENABLED_CHANGED_FLAG);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                // request available peers from the wifi p2p manager. This is an
                // asynchronous call and the calling activity is notified with a
                // callback on PeerListListener.onPeersAvailable()
                if (p2pManager != null)
                    p2pManager.requestPeers(channel, peerListListener);
                Log.d(LOG_TAG, "P2P peers changed");
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                if (p2pManager == null)
                    return;

                NetworkInfo networkInfo = (NetworkInfo) intent
                        .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                isConnected = networkInfo.isConnected();
                onStatusChanged(STATE_CONNECTED_CHANGED_FLAG);
                if (isConnected) {
                    Log.d(LOG_TAG, "P2P connected");
                    // we are connected with the other device, request connection
                    // info to find group owner IP

                    //DeviceDetailFragment fragment = (DeviceDetailFragment) activity
                    //        .getFragmentManager().findFragmentById(R.id.frag_detail);
                    //p2pManager.requestConnectionInfo(channel, fragment);
                } else {
                    Log.d(LOG_TAG, "P2P disconnected");
                    // It's a disconnect
                    //activity.resetData();
                }
            } else if (action.equals(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)) {
                Log.d(LOG_TAG, "P2P this device changed");
                thisDevice = (WifiP2pDevice)intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                onStatusChanged(STATE_DEVICE_CHANGED_FLAG);
            }
        }
    };*/

    /*private static final WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList peers) {
            Collection<WifiP2pDevice> devices = peers.getDeviceList();
            int i = 0,
                index = -1;

            // Remove unlisted peers
            synchronized (meshPeers) {
                for (MeshPeer peer : meshPeers) {
                    if (!devices.contains(peer.device))
                        meshPeers.remove(i);
                }

                // Add missing peers
                for (WifiP2pDevice device : devices) {
                    index = -1;
                    for (MeshPeer peer : meshPeers) {
                        if (peer.getName().equals(device.deviceName)) {
                            index = i;
                            break;
                        }
                    }
                    if (index < 0)
                        meshPeers.add(new MeshPeer(device));
                }

            // Alert listeners
                Log.v(LOG_TAG, "Peer list updated (" + meshPeers.size() + ", " + devices.size() + ")");
            }
            onPeersChanged();
        }
    };*/

    private static final ChannelListener channelListener = new ChannelListener() {
        /*@Override
        public void onSuccess() {
        }

        @Override
        public void onFailure ( int reasonCode){
        }

        @Override
        public void disconnect() {
            //final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager()
            //        .findFragmentById(R.id.frag_detail);
            //fragment.resetViews();
            p2pManager.removeGroup(channel, new ActionListener() {
                @Override
                public void onFailure(int reasonCode) {
                    Log.d(LOG_TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                    //fragment.getView().setVisibility(View.GONE);
                }
            });
        }

        @Override
        public void connect(WifiP2pConfig config) {
            p2pManager.connect(channel, config, new ActionListener() {
                @Override
                public void onSuccess() {
                    // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
                }

                @Override
                public void onFailure(int reason) {
                    //Toast.makeText(WiFiDirectActivity.this, "Connect failed. Retry.",
                    //        Toast.LENGTH_SHORT).show();
                }
            });
        }*/

        @Override
        public void onChannelDisconnected() {
            Log.d(LOG_TAG, "Channel disconnected");
            // we will try once more
            /*if (p2pManager != null) && !retryChannel) {
                Toast.makeText(this, "Channel lost. Trying again", Toast.LENGTH_LONG).show();
                resetData();
                retryChannel = true;
                p2pManager.initialize(this, getMainLooper(), this);
            } else {
                Toast.makeText(this,
                        "Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.",
                        Toast.LENGTH_LONG).show();
            }*/
        }
    };
}
