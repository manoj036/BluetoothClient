package com.example.manojkumar.bluetoothclient;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;

import com.bumptech.glide.Glide;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.nio.charset.Charset;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "Main Activity";
    public static final String MyUrl="http://orig06.deviantart.net/63b8/f/2011/365/e/2/luffy_wallpaper_by_dander97-d4kujok.png";
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    MyBluetoothService mBluetoothConnection;

    private ListView listView;
    private EditText box;
    private ArrayList<String> mDeviceList = new ArrayList<>();
    private ArrayList<BluetoothDevice> mBTDevices = new ArrayList<>();
    private BluetoothDevice mBTDevice;
    public boolean isConnected;

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
                bluetoothAdapter.cancelDiscovery();

                Log.d(TAG, "onItemClick: You Clicked on a device.");
                String deviceName = mBTDevices.get(i).getName();
                String deviceAddress = mBTDevices.get(i).getAddress();

                Log.d(TAG, "onItemClick: deviceName = " + deviceName);
                Log.d(TAG, "onItemClick: deviceAddress = " + deviceAddress);
                Log.d(TAG, "Trying to pair with " + deviceName);
                mBTDevices.get(i).createBond();

                mBTDevice = mBTDevices.get(i);

                mBluetoothConnection.startClient(mBTDevice);
            }
        });
    }


    static class MessageEvent{
        private String sendMessage;

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
        box.setText(event.getSendMessage());
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
        bluetoothAdapter.startDiscovery();
        IntentFilter discoverDevicesIntent = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(mReceiver, discoverDevicesIntent);
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
