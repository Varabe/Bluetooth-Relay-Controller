package varabe.brc.activity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import java.lang.ref.WeakReference;

import varabe.brc.DeviceData;
import varabe.brc.R;
import varabe.brc.bluetooth.DeviceConnector;

public class MainActivity extends AppCompatActivity {
    private static final String DEVICE_NAME = "DEVICE_NAME";
    static final String TAG = "MainActivity";

    // Intent request codes
    static final int REQUEST_CONNECT_DEVICE = 1;
    static final int REQUEST_ENABLE_BT = 2;
    static final int REQUEST_FINE_LOCATION_PERMISSION = 3;

    // Message types sent from the DeviceConnector Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    private static String MESSAGE_NOT_CONNECTED;
    private static String MESSAGE_CONNECTING;
    private static String MESSAGE_CONNECTED;

    // Relay commands
    public static final int COMMAND_ONE_SECOND_BLINK = 0;
    public static final int COMMAND_SWITCH = 1;
    public static final int COMMAND_INTERLOCK = 2;
    public static final int COMMAND_OPEN = 3;
    public static final int COMMAND_CLOSE = 4;

    // Colors TODO: figure out how to make them final
    public static int COLOR_GRAY;
    public static int COLOR_RED;

    private BluetoothAdapter btAdapter;
    private static DeviceConnector connector;
    private static BluetoothResponseHandler handler;
    private String deviceName;

    private static final String SAVED_PENDING_REQUEST_ENABLE_BT = "PENDING_REQUEST_ENABLE_BT";
    // do not resend request to enable Bluetooth
    // if there is a request already in progress
    // See: https://code.google.com/p/android/issues/detail?id=24931#c1
    boolean pendingRequestEnableBt = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        if (state != null) {
            pendingRequestEnableBt = state.getBoolean(SAVED_PENDING_REQUEST_ENABLE_BT);
        }
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter == null) {
            final String no_bluetooth = getString(R.string.no_bt_support);
            showAlertDialog(no_bluetooth);
            Log.d(TAG, "No bluetooth found");
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION_PERMISSION);
        }
        if (handler == null) handler = new BluetoothResponseHandler(this);
        else handler.setTarget(this);
        setupButtons();

        COLOR_GRAY = getResources().getColor(R.color.colorGray);
        COLOR_RED = getResources().getColor(R.color.colorRed);
        MESSAGE_NOT_CONNECTED = getString(R.string.message_not_connected);
        MESSAGE_CONNECTING = getString(R.string.message_connecting);
        MESSAGE_CONNECTED = getString(R.string.message_connected);
        if (isConnected() && (state != null))
            setDeviceName(state.getString(DEVICE_NAME));
        else
            getSupportActionBar().setSubtitle(MESSAGE_NOT_CONNECTED);
    }

    private void setupButtons() {
        View.OnTouchListener listener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) {
                    view.setBackgroundColor(COLOR_RED);
                    sendCommand(view, COMMAND_SWITCH);
                }
                else if (action == MotionEvent.ACTION_UP) {
                    view.setBackgroundColor(COLOR_GRAY);
                    sendCommand(view, COMMAND_SWITCH);
                }
                return true;
            }
        };
        findViewById(R.id.imageViewArrowUp).setOnTouchListener(listener);
        findViewById(R.id.imageViewArrowDown).setOnTouchListener(listener);
        findViewById(R.id.imageViewArrowLeft).setOnTouchListener(listener);
        findViewById(R.id.imageViewArrowRight).setOnTouchListener(listener);
        findViewById(R.id.imageViewArrowRotateLeft).setOnTouchListener(listener);
        findViewById(R.id.imageViewArrowRotateRight).setOnTouchListener(listener);
        findViewById(R.id.imageViewAudioSignal).setOnTouchListener(listener);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (btAdapter != null && !btAdapter.isEnabled() && !pendingRequestEnableBt) {
            pendingRequestEnableBt = true;
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }
    }
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVED_PENDING_REQUEST_ENABLE_BT, pendingRequestEnableBt);
        outState.putString(DEVICE_NAME, deviceName);
    }
    //    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

//     TODO: Find out why I need those
//        @Override
//        public synchronized void onPause() {
//            super.onPause();
//        }
//        @Override
//        public synchronized void onResume() {
//            super.onResume();

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case R.id.action_bluetooth:
                if (isAdapterReady()) {
                    if (isConnected()) stopConnection();
                    else startDeviceListActivity();
                } else {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
                return true;
            default:
                Log.d(TAG, "User clicked item in menu that we don't support yet");
                return true;
        }
    }
    private void setupConnector(BluetoothDevice connectedDevice) {
        stopConnection();
        try {
            String name = getString(R.string.unknown_device_name);
            DeviceData data = new DeviceData(connectedDevice, name);
            connector = new DeviceConnector(data, handler);
            connector.connect();
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "setupConnector failed: " + e.getMessage());
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    String address = data.getStringExtra(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    BluetoothDevice device = btAdapter.getRemoteDevice(address);
                    if (isAdapterReady() && (connector == null)) setupConnector(device);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                pendingRequestEnableBt = false;
                if (resultCode != Activity.RESULT_OK) {
                    Log.d(TAG, "BT not enabled");
                }
                break;
        }
    }

    boolean isAdapterReady() {
        return (btAdapter != null) && (btAdapter.isEnabled());
    }
    private boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }

    void stopConnection() {
        if (connector != null) {
            connector.stop();
            connector = null;
            deviceName = null;
        }
    }

    private void startDeviceListActivity() {
        stopConnection();
        Intent discoverBtDevicesIntent = new Intent(this, DeviceListActivity.class);
        startActivityForResult(discoverBtDevicesIntent, REQUEST_CONNECT_DEVICE);
    }
    void showAlertDialog(String message) {
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setTitle(getString(R.string.app_name));
        alertDialogBuilder.setMessage(message);
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }
    void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        getSupportActionBar().setSubtitle(deviceName);
    }

    private static class BluetoothResponseHandler extends Handler {

        private WeakReference<MainActivity> mActivity;
        public BluetoothResponseHandler(MainActivity activity) {
            mActivity = new WeakReference<MainActivity>(activity);
        }

        public void setTarget(MainActivity target) {
            mActivity.clear();
            mActivity = new WeakReference<MainActivity>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            // Меняет текст на ActionBar, чтобы оповестить пользователя о смене состояния подключения
            MainActivity activity = mActivity.get();
            if (activity != null) {
                switch (msg.what) {
                    case MESSAGE_STATE_CHANGE:
                        Log.d(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                        final ActionBar bar = activity.getSupportActionBar();
                        switch (msg.arg1) {
                            case DeviceConnector.STATE_CONNECTED:
                                bar.setSubtitle(MESSAGE_CONNECTED);
                                break;
                            case DeviceConnector.STATE_CONNECTING:
                                bar.setSubtitle(MESSAGE_CONNECTING);
                                break;
                            case DeviceConnector.STATE_NONE:
                                bar.setSubtitle(MESSAGE_NOT_CONNECTED);
                                break;
                        }
                        activity.invalidateOptionsMenu();
                        break;

                    case MESSAGE_DEVICE_NAME:
                        activity.setDeviceName((String) msg.obj);
                        break;

                    case MESSAGE_WRITE:
                        // stub
                        break;

                    case MESSAGE_TOAST:
                        // stub
                        break;
                }
            }
        }

    }

    // ================= Commands =================
    public void onSwitchButtonClick(View view) {
        if (((ColorDrawable) view.getBackground()).getColor() == COLOR_GRAY) {
            view.setBackgroundColor(COLOR_RED);
            sendCommand(view, COMMAND_CLOSE);
        }
        else {
            view.setBackgroundColor(COLOR_GRAY);
            sendCommand(view, COMMAND_OPEN);
        }
    }
    public void onBlinkButtonClick(final View view) {
        sendCommand(view, COMMAND_SWITCH);
        view.setBackgroundColor(COLOR_RED);
        view.setEnabled(false);
        CountDownTimer timer = new CountDownTimer(5000, 5000) {
            @Override
            public void onTick(long l) {}

            @Override
            public void onFinish() {
                sendCommand(view, COMMAND_SWITCH);
                view.setBackgroundColor(COLOR_GRAY);
                view.setEnabled(true);

            }
        }.start();
    }
    private void sendCommand(View view, int command) {
        String relayChannelAssociatedWithView = view.getTag().toString();
        sendCommand(relayChannelAssociatedWithView, command);
    }
    private void sendCommand(String relayChannel, int command) {
        sendCommand(relayChannel + command);
    }
    public void sendCommand(String commandString) {
        if (!commandString.isEmpty() && isConnected()) {
            final String COMMAND_ENDING = "\r\n";
            byte[] command = (commandString + COMMAND_ENDING).getBytes();
            connector.write(command);
        }
    }
}
