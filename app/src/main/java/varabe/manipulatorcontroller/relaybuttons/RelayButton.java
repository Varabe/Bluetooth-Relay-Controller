package varabe.manipulatorcontroller.relaybuttons;

import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import varabe.manipulatorcontroller.RelayController;

import static varabe.manipulatorcontroller.RelayController.COMMAND_CLOSE;
import static varabe.manipulatorcontroller.RelayController.COMMAND_OPEN;
import static varabe.manipulatorcontroller.RelayController.SUPPORTED_CHANNELS;
import static varabe.manipulatorcontroller.activity.MainActivity.COLOR_GRAY;
import static varabe.manipulatorcontroller.activity.MainActivity.COLOR_RED;

abstract public class RelayButton {
    public int getId() {
        return view.getId();
    }

    public String getRelayChannel() {
        return relayChannel;
    }

    public RelayController getController() {
        return controller;
    }

    public View getView() {
        return view;
    }

    private static ArrayList<RelayButton> buttons = new ArrayList<>();
    private String relayChannel;
    private View view;
    private RelayController controller;
    private int timeout;
    private boolean hasActiveTask;
    private MutuallyExclusiveButtonManager MEBManager;

    RelayButton(View view, String relayChannel, RelayController controller, int timeout) {
        this.view = view;
        this.relayChannel = relayChannel;
        this.controller = controller;
        this.timeout = timeout;
        this.hasActiveTask = false;
        buttons.add(this);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RelayButton)
            return ((RelayButton) obj).getId() == getId();
        else
            return false;
    }

    void setMEBManager(MutuallyExclusiveButtonManager MEBManager) {
        this.MEBManager = MEBManager;
    }

    public void setEnabled(Boolean enabled) {
        if (!hasActiveTask) {
            if (view instanceof ImageView)
                setEnabled((ImageView) view, enabled);
            else if (view instanceof Button)
                view.setEnabled(enabled);
            else
                throw new UnsupportedOperationException("View of type \"" + view.getClass() + "\" is not supported");
        }
    }
    private void setEnabled(ImageView view, Boolean enabled) {
        view.setEnabled(enabled);
        if (enabled)
            view.setColorFilter(null);
        else
            view.setColorFilter(Color.argb(255,150,150,150));
    }

    public void onActivate() {
        activate();
        view.setBackgroundColor(COLOR_RED);
        setEnabledMutuallyExclusiveButtons(false);
    }
    public void onDeactivate() {
        deactivate();
        view.setBackgroundColor(COLOR_GRAY);
        setEnabledMutuallyExclusiveButtons(true);
        if (timeout > 0) {
            hasActiveTask = true;
            new Timer().schedule(new EnablingTask(), timeout);
        }
    }
    void activate() {
        controller.sendCommand(relayChannel, COMMAND_CLOSE);
    }
    void deactivate() {
        controller.sendCommand(relayChannel, COMMAND_OPEN);
    }

    static String getRelayChannelFromViewTag(View view) {
        Object tagObj = view.getTag();
        if (tagObj == null)
            throw new UnsupportedOperationException("View tag is not set (View ID: " + view.getId() + ")");
        else {
            String tag = tagObj.toString();
            for (String supportedTag: SUPPORTED_CHANNELS) {
                if (tag.equals(supportedTag))
                    return tag;
            }
            throw new UnsupportedOperationException("View tag '" + tag + "' is not supported (View ID: " + view.getId() + ")");
        }
    }

    private void setEnabledMutuallyExclusiveButtons(boolean enabled) {
        if (MEBManager != null)
            MEBManager.setEnabledMutuallyExclusiveButtons(this, enabled);
    }
    private class EnablingTask extends TimerTask {

        public void run() {
            hasActiveTask = false;
            setEnabled(true);
        }
    }
    public static final void clearButtons() {
        buttons.clear();
    }
    public static final void setEnabledAllButtons(boolean enabled) {
        for (RelayButton button: buttons) {
            button.setEnabled(enabled);
        }
    }
}
