package com.example.manojkumar.bluetoothclient;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import static com.example.manojkumar.bluetoothclient.MainActivity.STATE_CONNECTED;
import static com.example.manojkumar.bluetoothclient.MainActivity.STATE_CONNECTING;
import static com.example.manojkumar.bluetoothclient.MainActivity.STATE_LISTEN;
import static com.example.manojkumar.bluetoothclient.MainActivity.STATE_NONE;

class MyBluetoothService extends Activity{
    private static final String TAG = "BluetoothService";
    private static final String appName = "My_App";
    private static final UUID MY_UUID = UUID.fromString("e381afeb-523e-44c6-a81a-f90508ea5a2e");
    private final BluetoothAdapter mBluetoothAdapter;

    private BluetoothSocket mmSocket;
    private BluetoothServerSocket mmServerSocket;
    private InputStream mmInStream;
    private OutputStream mmOutStream;

    private ProgressDialog mProgressDialog;

    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;

    private Context mContext;
    private MainActivity.MessageEvent messageEvent;
    private AcceptThread mInsecureAcceptThread;

    MyBluetoothService(Context context) {
        mContext=context;
        messageEvent=new MainActivity.MessageEvent();
        messageEvent.setmState(STATE_NONE);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Toast.makeText(context,"Bluetooth Service Initiated",Toast.LENGTH_SHORT).show();
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
            messageEvent.setmState(STATE_LISTEN);
            EventBus.getDefault().post(messageEvent);
        }
        public void run(){
            Log.d(TAG, "run: AcceptThread Running.");
            BluetoothSocket socket = null;
            while (messageEvent.getmState()!=STATE_CONNECTED) {
                try {
                    Log.d(TAG, "run: RFCOM server socket start.....");
                    socket = mmServerSocket.accept();
                    Log.d(TAG, "run: RFCOM server socket accepted connection.");
                } catch (IOException e) {
                    Log.e(TAG, "Socket's accept() method failed", e);
                }
                if (socket != null) {
                    synchronized (MyBluetoothService.this) {
                        switch (messageEvent.getmState()) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                mConnectedThread = new ConnectedThread(socket);
                                mConnectedThread.start();
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            Log.d(TAG,"cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG,"close() of server failed", e);
            }
        }

    }

    private class ConnectThread extends Thread {

        ConnectThread(BluetoothDevice device) {
            // Cancel any thread attempting to make a connection
            if (messageEvent.getmState() == STATE_CONNECTING) {
                if (mConnectThread != null) {
                    mConnectThread.cancel();
                    mConnectThread = null;
                }
            }
                // Cancel any thread currently running a connection
            if (mConnectedThread != null) {
                mConnectedThread.cancel();
                mConnectedThread = null;
            }
            try {
                mmSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket's create() method failed", e);
            }
            messageEvent.setmState(STATE_CONNECTING);
            EventBus.getDefault().post(messageEvent);
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
                connectionFailed();
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


    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread();
            mInsecureAcceptThread.start();
        }
    }


    void startClient(BluetoothDevice device){
        Log.d(TAG, "startClient: Started.");

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
                tmpOut = mmSocket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating IO streams", e);
            }
            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            messageEvent.setmState(STATE_CONNECTED);
            EventBus.getDefault().post(messageEvent);
        }

        public void run(){
            byte[] mmBuffer = new byte[1024];

            int numBytes; // bytes returned from read()
            while (messageEvent.getmState()==STATE_CONNECTED) {
                try {
                    numBytes = mmInStream.read(mmBuffer);
                    final String incomingMessage = new String(mmBuffer, 0, numBytes);
                    Log.d(TAG, "InputStream: " + incomingMessage);
                    messageEvent.setDataReceived(true);
                    messageEvent.setSendMessage(incomingMessage);
                    EventBus.getDefault().post(messageEvent);
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                mmOutStream.write(buffer);
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }

    void write(byte[] bytes) {
        ConnectedThread r;
        synchronized (this) {
            if (messageEvent.getmState() != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        r.write(bytes);
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

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }


        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        messageEvent.setmState(STATE_NONE);
        EventBus.getDefault().post(messageEvent);
        // Update UI title
    }
    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        messageEvent.setmState(STATE_NONE);
        EventBus.getDefault().post(messageEvent);

        // Start the service over to restart listening mode
        MyBluetoothService.this.start();
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        messageEvent.setmState(STATE_NONE);
        EventBus.getDefault().post(messageEvent);

        // Start the service over to restart listening mode
        MyBluetoothService.this.start();
    }

}
