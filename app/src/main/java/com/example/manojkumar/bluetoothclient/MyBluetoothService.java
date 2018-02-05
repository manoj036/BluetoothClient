package com.example.manojkumar.bluetoothclient;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

class MyBluetoothService{
    private static final String TAG = "BluetoothService";
    private static final String appName = "My_App";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB6");
    private final BluetoothAdapter mBluetoothAdapter;

    private BluetoothSocket mmSocket;
    private BluetoothServerSocket mmServerSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    private ProgressDialog mProgressDialog;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private Context mContext;
    private MainActivity.MessageEvent messageEvent = new MainActivity.MessageEvent();
    private AcceptThread mInsecureAcceptThread;

    MyBluetoothService(Context context) {
        mContext=context;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        start();
    }

    private class AcceptThread extends Thread {
        AcceptThread(){
            try{
                mmServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord(appName, MY_UUID);
                Log.d(TAG, "AcceptThread: Setting up Server using: " + MY_UUID);
            }catch (IOException e){
                Log.e(TAG, "Socket's listen() method failed", e);
            }
        }
        public void run(){
            Log.d(TAG, "run: AcceptThread Running.");
            BluetoothSocket socket = null;
            try{
                Log.d(TAG, "run: RFCOM server socket start.....");
                socket = mmServerSocket.accept();
                Log.d(TAG, "run: RFCOM server socket accepted connection.");
            }catch (IOException e){
                Log.e(TAG, "Socket's accept() method failed", e);
            }
            if(socket != null){
                mConnectedThread = new ConnectedThread(socket);
                mConnectedThread.start();
            }
        }
    }

    private class ConnectThread extends Thread {

        ConnectThread(BluetoothDevice device) {
            resetConnection();
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
        }

        public void run() {
            mBluetoothAdapter.cancelDiscovery();
            try {
                mmSocket.connect();
            } catch (IOException connectException) {
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close the client socket", closeException);
                }
                return;
            }
            mConnectedThread = new ConnectedThread(mmSocket);
            mConnectedThread.start();
        }
        void cancel() {
            try {
                Log.d(TAG, "cancel: Closing Client Socket.");
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "cancel: close() of mmSocket in Connectthread failed. " + e.getMessage());
            }
        }
    }

    private synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }

    void startClient(BluetoothDevice device){
        Log.d(TAG, "startClient: Started.");

        mProgressDialog = ProgressDialog.show(mContext,"Connecting Bluetooth"
                ,"Please Wait...",true);
        mConnectThread = new ConnectThread(device);
        mConnectThread.start();
    }

    private class ConnectedThread extends Thread {
        ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            Log.d(TAG, "ConnectedThread: Devices connected");
            //dismiss the progressdialog when connection is established
            if(mProgressDialog!=null) {
                try {
                    mProgressDialog.dismiss();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                }
            }
            try {
                tmpIn = mmSocket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run(){
            byte[] mmBuffer = new byte[1024];

            int numBytes; // bytes returned from read()
            while (true) {
                try {
                    numBytes = mmInStream.read(mmBuffer);
                    final String incomingMessage = new String(mmBuffer, 0, numBytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);
                    messageEvent.setSendMessage(incomingMessage);
                    EventBus.getDefault().post(messageEvent);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }
    }

    void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);

        } catch (IOException e) {
            Log.e(TAG, "Couldn't send data to the other device " + e.getMessage() );
        }
    }

    private void resetConnection() {
        if (mmInStream != null) {
            try {mmInStream.close();} catch (Exception ignored) {}
            mmInStream = null;
        }
        if (mmOutStream != null) {
            try {mmOutStream.close();} catch (Exception ignored) {}
            mmOutStream = null;
        }
        if (mmSocket != null) {
            try {
                Thread.sleep(1000);
                mmSocket.close();} catch (Exception ignored) {}
            mmSocket = null;
        }
    }
}
