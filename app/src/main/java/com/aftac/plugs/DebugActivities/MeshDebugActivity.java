package com.aftac.plugs.DebugActivities;

import android.net.wifi.p2p.WifiP2pDevice;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.aftac.plugs.MeshNetwork.MeshManager;
import com.aftac.plugs.MeshNetwork.MeshDevice;
import com.aftac.plugs.R;

import java.util.List;

public class MeshDebugActivity extends AppCompatActivity
                implements MeshManager.MeshPeersChangedListener,
                           MeshManager.MeshStatusChangedListener {
    private static final String LOG_TAG = MeshDebugActivity.class.getSimpleName();

    RecyclerView peerListView;
    RecyclerView.LayoutManager listLayoutManager;
    ListView listView;
    TextView read_msg_box, connectionStatus;
    EditText writeMSg;
    List<MeshDevice> availableDevices;
    List<MeshDevice> connectedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_mesh2);

        peerListView = (RecyclerView) findViewById(R.id.mesh_peers_list);
        peerListView.setHasFixedSize(true);

        listLayoutManager = new LinearLayoutManager(this);
        peerListView.setLayoutManager(listLayoutManager);

        peerListView.setAdapter(peerListAdapter);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //read_msg_box = findViewById(R.id.txt_debug_mesh);

        if (!MeshManager.isEnabled())
            MeshManager.init(getBaseContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        //if (!MeshManager.isConnected() && !MeshManager.isListening())
        MeshManager.startPeerDiscovery();
        MeshManager.addOnPeersChangedListener(this);
        MeshManager.addOnStatusChangedListener(this);

        if (MeshManager.isListening())
            setStatusText("Listening for peers");
        else if (MeshManager.isConnected())
            setStatusText("Connected to network");
        else if (MeshManager.isEnabled())
            setStatusText("Idle");
    }

    @Override
    public void onPause() {
        super.onPause();
        MeshManager.stopPeerDiscovery();
        MeshManager.removeOnPeersChangedListener(this);
        MeshManager.removeOnStatusChangedListener(this);
    }

    private void setStatusText(String str) {
        TextView txtView = findViewById(R.id.txt_mesh_status);
        txtView.setText(str);
    }

    @Override
    public void onMeshPeersChanged(List<MeshDevice> available, List<MeshDevice> connected) {
        Log.v(LOG_TAG, "Peer list updated");
        availableDevices = available;
        connectedDevices = connected;
        peerListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMeshStatusChanged(int statusFlags) {
        Log.d(LOG_TAG, "Status changed: 0x" + Integer.toHexString(statusFlags & 0xFF00));
        switch (statusFlags & 0xFF00) {
            case MeshManager.STATE_ENABLED_CHANGED_FLAG:
            break;
            case MeshManager.STATE_LISTENING_CHANGED_FLAG:
                setStatusText(
                        ((statusFlags & MeshManager.STATE_LISTENING_FLAG) > 0) ?
                        "Listening for peers" :
                        "Listening stopped");
            break;
            case MeshManager.STATE_CONNECTED_CHANGED_FLAG:
                setStatusText(
                        ((statusFlags & MeshManager.STATE_CONNECTED_FLAG) > 0) ?
                        "Connected to network" :
                        "Disconnected from network");
                peerListAdapter.notifyDataSetChanged();
            break;
            case MeshManager.STATE_ERROR_OCCURRED_FLAG:
            break;
        }
    }

    private RecyclerView.Adapter peerListAdapter = new RecyclerView.Adapter() {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View v = inflater.inflate(R.layout.activity_debug_mesh_row_devices, parent, false);

            return new PeerViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int length = connectedDevices.size();
            if (position < length)
                ((PeerViewHolder)holder).setDevice(connectedDevices.get(position), true);
            else
                ((PeerViewHolder)holder).setDevice(availableDevices.get(position - length), false);
        }

        @Override
        public int getItemCount() {
            return  (availableDevices != null ? availableDevices.size() : 0)
                  + (connectedDevices != null ? connectedDevices.size() : 0);
        }

        class PeerViewHolder extends RecyclerView.ViewHolder {
            public View myView;
            private String deviceName;
            private String deviceId;

            public PeerViewHolder(View v) {
                super(v);
                v.findViewById(R.id.device_name);

                myView = v;
                myView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MeshManager.connectTo(deviceId);
                    }
                });
            }

            void setDevice(MeshDevice device, boolean connected) {
                this.deviceName = device.getName();
                this.deviceId = device.getId();
                ((TextView)myView.findViewById(R.id.device_name)).setText(deviceName + " (" + deviceId + ")");
                ((TextView)myView.findViewById(R.id.device_details)).setText(
                        (connected ? "Connected" : "Available"));
            }

            /*String convertStatus(int status) {
                switch (device.getStatus()) {
                    case WifiP2pDevice.AVAILABLE:
                        return "Available";
                    case WifiP2pDevice.INVITED:
                        return "Invited";
                    case WifiP2pDevice.CONNECTED:
                        return "Connected";
                    case WifiP2pDevice.FAILED:
                        return "Failed";
                    case WifiP2pDevice.UNAVAILABLE:
                        return "Unavailable";
                    default:
                        return "Unknown";
                }
            }*/
        }
    };
}