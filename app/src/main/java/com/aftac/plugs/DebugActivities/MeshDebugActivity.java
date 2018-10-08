package com.aftac.plugs.DebugActivities;

import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.aftac.plugs.MeshNetwork.MeshManager;
import com.aftac.plugs.MeshNetwork.MeshDevice;
import com.aftac.plugs.Queue.Queue;
import com.aftac.plugs.R;

import java.util.List;

public class MeshDebugActivity extends AppCompatActivity
                implements MeshManager.MeshPeersChangedListener,
                           MeshManager.MeshStatusChangedListener {
    private static final String LOG_TAG = MeshDebugActivity.class.getSimpleName();

    RecyclerView peerListView;
    RecyclerView.LayoutManager listLayoutManager;
    TextView txtStatus;
    List<MeshDevice> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_mesh);

        peerListView = findViewById(R.id.mesh_peers_list);
        peerListView.setHasFixedSize(true);

        listLayoutManager = new LinearLayoutManager(this);
        peerListView.setLayoutManager(listLayoutManager);

        peerListView.setAdapter(peerListAdapter);

        txtStatus = findViewById(R.id.txt_mesh_status);
        TextView txtLocalName = findViewById(R.id.txt_mesh_local_name);
        txtLocalName.setText(Queue.getName());


        if (!MeshManager.isEnabled())
            MeshManager.init(getBaseContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        MeshManager.startPeerDiscovery();
        MeshManager.addOnPeersChangedListener(this);
        MeshManager.addOnStatusChangedListener(this);

        if (MeshManager.isConnected())
            setStatusText("Connected to network");
        else if (MeshManager.isListening())
            setStatusText("Listening for peers");
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
    public void onMeshPeersChanged(List<MeshDevice> devices) {
        Log.v(LOG_TAG, "Peer list updated");
        this.devices = devices;
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
            View v = inflater.inflate(R.layout.activity_mesh_device_row, parent, false);

            return new PeerViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            MeshDevice device = devices.get(position);
            ((PeerViewHolder)holder).setDevice(device, device.isConnected());
        }

        @Override
        public int getItemCount() {
            return  (devices != null ? devices.size() : 0);
        }

        class PeerViewHolder extends RecyclerView.ViewHolder {
            private View myView;
            private String deviceName;
            private String deviceId;

            PeerViewHolder(View view) {
                super(view);
                view.findViewById(R.id.device_name);

                myView = view;
                myView.setOnClickListener((v) -> MeshManager.connectTo(deviceId));
            }

            void setDevice(MeshDevice device, boolean connected) {
                this.deviceName = device.getName();
                this.deviceId = device.getId();
                ((TextView)myView.findViewById(R.id.device_name))
                            .setText(deviceName + " (" + deviceId + ")");
                ((TextView)myView.findViewById(R.id.device_details)).setText(
                            (device.isConnected() ? "Connected" :
                            (device.isConnecting() ? "Connecting" : "Available")));
            }
        }
    };
}