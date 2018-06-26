package varabe.brc.RelayController;

import android.bluetooth.BluetoothDevice;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import varabe.brc.DeviceData;
import varabe.brc.R;
import varabe.brc.activity.MainActivity;
import varabe.brc.bluetooth.DeviceConnector;

import static varabe.brc.activity.MainActivity.COLOR_GRAY;
import static varabe.brc.activity.MainActivity.COLOR_RED;
/*
 * Holding button implementation is based on "oneSecondBlinkSequence" which, instead of sending
 * "COMMAND_SWITCH" twice (on press and on release), sends "COMMAND_ONE_SECOND_BLINK" continuously.
 * The reason is: if device turns off or bluetooth connection is broken while a user holds a button,
 * the corresponding relay channel will stay active which might be dangerous. So, in this
 * implementation, if relay board does not get any new requests, it turns a relay off automatically
 */

// The class handles command sending, listening for clicks, and connecting/disconnecting
public class RelayController {
    private static final String TAG = "RelayController";

    // Relay commands
    public static final int COMMAND_ONE_SECOND_BLINK = 0;
    public static final int COMMAND_SWITCH = 1;
    public static final int COMMAND_INTERLOCK = 2;
    public static final int COMMAND_OPEN = 3;
    public static final int COMMAND_CLOSE = 4;

    private WeakReference<MainActivity> activity;
    private static DeviceConnector connector;
    private final View.OnTouchListener holdingButtonListener = new HoldingButtonListener();
    private final View.OnClickListener switchButtonListener = new SwitchButtonListener();
    private Timer timer = new Timer();
    private CommandOneSecondBlinkExecutorTask currentTask;
    private Set<WeakReference<View>> buttonSet; // Should I use weakreference here?

    public RelayController(MainActivity activity) {
        this.activity = new WeakReference<>(activity);
        this.buttonSet = new HashSet<>();
    }
    public void addHoldingButton(View view) {
        view.setOnTouchListener(holdingButtonListener);
        buttonSet.add(new WeakReference<>(view));
    }
    public void addSwitchButton(View view) {
        view.setOnClickListener(switchButtonListener);
        buttonSet.add(new WeakReference<>(view));
    }
//    public void onBlinkButtonClick(final View view) { // might be useful in the future, safe to delete
//        sendCommand(view, COMMAND_SWITCH);
//        view.setBackgroundColor(COLOR_RED);
//        view.setEnabled(false);
//        CountDownTimer timer = new CountDownTimer(5000, 5000) {
//            @Override
//            public void onTick(long l) {}
//
//            @Override
//            public void onFinish() {
//                sendCommand(view, COMMAND_SWITCH);
//                view.setBackgroundColor(COLOR_GRAY);
//                view.setEnabled(true);
//
//            }
//        }.start();
//    }
    public void sendCommand(View view, int command) {
        String relayChannelAssociatedWithView = view.getTag().toString();
        sendCommand(relayChannelAssociatedWithView, command);
    }
    public void sendCommand(String relayChannel, int command) {
        sendCommand(relayChannel + command);
    }
    public void sendCommand(String commandString) {
        if (!commandString.isEmpty() && isConnected()) {
            final String COMMAND_ENDING = "\r\n";
            byte[] command = (commandString + COMMAND_ENDING).getBytes();
            connector.write(command);
        }
    }
    // Connector-related methods
    public void connect(BluetoothDevice connectedDevice) {
        stopConnection();
        MainActivity activity = this.activity.get();
        if (activity != null) {
            try {
                String name = activity.getString(R.string.unknown_device_name);
                DeviceData data = new DeviceData(connectedDevice, name);
                connector = new DeviceConnector(data, activity.handler);
                connector.connect();
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "setupConnector failed: " + e.getMessage());
            }
        }
    }

    public boolean isConnected() {
        return (connector != null) && (connector.getState() == DeviceConnector.STATE_CONNECTED);
    }
    public void stopConnection() {
        MainActivity activity = this.activity.get();
        if (connector != null && activity != null) {
            connector.stop();
            connector = null;
            activity.setDeviceName(null);
        }
    }

    // Timer management classes and methods
    private class HoldingButtonListener implements View.OnTouchListener {
        @Override
        public boolean onTouch(View view, MotionEvent event) {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN) { // removed check for currentTask == null
                    view.setBackgroundColor(COLOR_RED);
                    scheduleRelayBlinkSequence(view);
                } else if (action == MotionEvent.ACTION_UP) { // removed check for currentTask != null
                    view.setBackgroundColor(COLOR_GRAY);
                    stopRelayBlinkSequence(view);

                }
            return true;
        }
    }
    private class SwitchButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            // TODO: Test if pressing switch and hold will result in the same bug as holding two holds at once
            if (((ColorDrawable) view.getBackground()).getColor() == COLOR_GRAY) {
                view.setBackgroundColor(COLOR_RED);
                sendCommand(view, COMMAND_CLOSE);
            }
            else {
                view.setBackgroundColor(COLOR_GRAY);
                sendCommand(view, COMMAND_OPEN);
            }
        }
    }
    private class CommandOneSecondBlinkExecutorTask extends TimerTask {
        private View view;
        CommandOneSecondBlinkExecutorTask(View view) {
            super();
            this.view = view;
        }
        public void run() {
            sendCommand(view, COMMAND_ONE_SECOND_BLINK);
        }
        public View getView() {
            return view;
        }
    }
    private void scheduleRelayBlinkSequence(View view) {
        currentTask = new CommandOneSecondBlinkExecutorTask(view);
        timer.scheduleAtFixedRate(currentTask, 0, 400);
        setEnabledAllButtonsExcept(view, false);
    }
    private void stopRelayBlinkSequence(final View view) {
        if (currentTask.getView().equals(view)) {
            currentTask.cancel();
//            currentTask = null; // TODO Delete
            new CountDownTimer(1000, 1000) {
                // When board is still evaluating the last 1 second command (which is very rare),
                // it will result in a bug that will leave one of the relays active. If we wait for
                // the board to finish, the bug has no chance of occurring. 1000 millis is the worst
                // case scenario
                @Override
                public void onTick(long l) {}
                @Override
                public void onFinish() {
                    currentTask = null;
                    setEnabledAllButtonsExcept(view, true);
                    setEnabledImageView((ImageView) view, true);
                }
            }.start();

            sendCommand(view, COMMAND_OPEN); // TODO: Test how the bug will occur without this line
            setEnabledImageView((ImageView) view, false);
        }
    }
    private void setEnabledAllButtonsExcept(View view, boolean enabled) {
        // Might need to be optimized
        for (WeakReference buttonReference: buttonSet) {
            View button = (View) buttonReference.get();
            if (!view.equals(button)) {
                setEnabledImageView((ImageView) button, enabled); // BEWARE, WILL RESULT IN AN ERROR IF WE HAVE AN ACTUAL BUTTON
            }
        }
    }
    private void setEnabledImageView(ImageView view, Boolean enabled) {
        view.setEnabled(enabled);
        if (enabled)
            view.setColorFilter(null);
        else
            view.setColorFilter(Color.argb(255,150,150,150));
    }
    public void deactivateAllAvailibleRelayChannels() {
        for (WeakReference buttonReference: buttonSet) {
            View view = (View) buttonReference.get();
            if (view != null)
                sendCommand(view, COMMAND_OPEN);
        }
    }
}
