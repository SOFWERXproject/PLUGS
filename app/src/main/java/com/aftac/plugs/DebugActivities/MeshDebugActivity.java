package com.aftac.plugs.DebugActivities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.aftac.plugs.R;

public class MeshDebugActivity extends AppCompatActivity {

    Button btnOnOff, btnDiscovery, btnSend;
    ListView listView;
    TextView read_msg_box, connectionStatus;
    EditText writeMSg;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_debug_mesh);
        initialWork();
    }

    private void initialWork() {
        btnOnOff=(Button) findViewById(R.id.onOff);
        btnDiscovery=(Button) findViewById(R.id.discover);
        btnSend=(Button) findViewById(R.id.sendButton);
        listView=(ListView) findViewById(R.id.peerListView);
        read_msg_box=(TextView) findViewById(R.id.readMsg);
        connectionStatus=(TextView) findViewById(R.id.connectionStatus);
        writeMSg=(EditText) findViewById(R.id.writeMsg);
    }
}