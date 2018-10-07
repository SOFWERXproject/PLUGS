package com.aftac.plugs.DebugActivities;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.ViewGroup;
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

        ViewGroup rootView = findViewById(R.id.action_bar_root);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }
}