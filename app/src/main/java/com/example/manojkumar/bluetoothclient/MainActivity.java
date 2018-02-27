package com.example.manojkumar.bluetoothclient;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.charset.Charset;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
//    public static final String MyUrl="http://orig06.deviantart.net/63b8/f/2011/365/e/2/luffy_wallpaper_by_dander97-d4kujok.png";
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    static MyBluetoothService mBluetoothConnection;

    private ListView listView;
    private EditText box;
    public ArrayList<String> mDeviceList = new ArrayList<>();
    public ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    public BluetoothDevice mBTDevice;
    public boolean isConnected;

    // Constants that indicate the current connection state
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        listView =  findViewById(R.id.listView);
        box=  findViewById(R.id.box);
//        imageView=findViewById(R.id.imageView);
//        Glide.with(MainActivity.this).load(MyUrl).into(imageView);
        checkBTPermissions();
        requestStoragePermissions();
        if(bluetoothAdapter==null){
            Log.d(TAG, "onCreate: Device Doesnt Support Bluetooth");
        }
        else Log.d(TAG, "onCreate: Bluetooth Available");

        if(bluetoothAdapter.isEnabled()){
            mBluetoothConnection = new MyBluetoothService(MainActivity.this);
        }

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

            }
        });
    }


    static class MessageEvent{
        private String sendMessage;
        private int mState;
        private boolean dataReceived;

        public int getmState() {
            return mState;
        }

        public boolean getDataReceived() {
            return dataReceived;
        }

        public void setDataReceived(boolean dataReceived) {
            this.dataReceived = dataReceived;
        }

        public void setmState(int mState) {
            this.mState = mState;
        }

        String getSendMessage() {
            return sendMessage;
        }

        void setSendMessage(String sendMessage) {
            this.sendMessage = sendMessage;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event){
        Log.d(TAG, "onMessageEvent: "+event.getSendMessage());
        int mState=event.getmState();
        if(event.getDataReceived()) {
            Toast.makeText(MainActivity.this,"Data Received",Toast.LENGTH_SHORT).show();
            box.setText(event.getSendMessage());
        }else switch (mState){
            case STATE_NONE:
                Toast.makeText(MainActivity.this,"Disconnected",Toast.LENGTH_SHORT).show();
                break;
            case STATE_CONNECTED:
                Toast.makeText(MainActivity.this,"Connected",Toast.LENGTH_SHORT).show();
                break;
            case STATE_LISTEN:
                Toast.makeText(MainActivity.this,"Listening for New Devices",Toast.LENGTH_SHORT).show();
                break;
            case STATE_CONNECTING:
                Toast.makeText(MainActivity.this,"Connecting",Toast.LENGTH_SHORT).show();
                break;
        }
    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, "onReceive: ACTION FOUND.");

            assert action != null;
            switch (action) {
                case BluetoothDevice.ACTION_FOUND:
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    Log.d(TAG, "onReceive: " + device.getName() + ": " + device.getAddress());
                    mBTDevices.add(device);
                    mDeviceList.add(device.getName() + "\n" + device.getAddress());
                    listView.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, mDeviceList));
                    break;
                case BluetoothDevice.ACTION_ACL_CONNECTED:
                    Log.d(TAG, "onReceive: Connected");
                    isConnected = true;
                    break;
                case BluetoothDevice.ACTION_ACL_DISCONNECTED:
                    Log.d(TAG, "onReceive: Disconnected");
                    isConnected = false;
                    break;
            }
        }
    };

    public void Bluetooth_on(View view) {
        Log.d(TAG, "Bluetooth_on: ON pressed");
        if (!bluetoothAdapter.isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent,1);
            mBluetoothConnection = new MyBluetoothService(MainActivity.this);
        }
    }

    public void SendBT(View view) {
        byte[] bytes = box.getText().toString().getBytes(Charset.defaultCharset());
        mBluetoothConnection.write(bytes);
    }

    public void Bluetooth_off(View view) {
        bluetoothAdapter.disable();
    }

    public void Bluetooth_discoverable(View view) {
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverableIntent);
    }

    public void Bluetooth_discover(View view) {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.");
        mDeviceList = new ArrayList<>();
        Intent serverIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(serverIntent,1);
    }

    private void checkBTPermissions() {
        int permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION");
        permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION");
        if (permissionCheck != 0) {
            this.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 1001); //Any number
        }
    }


    private void requestStoragePermissions() {
        if(this.checkSelfPermission("Manifest.permission.READ_EXTERNAL_STORAGE")!= PackageManager.PERMISSION_GRANTED){
            this.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},1);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy: Destroyed");
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

}
