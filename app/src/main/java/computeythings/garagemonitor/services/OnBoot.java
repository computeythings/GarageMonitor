package computeythings.garagemonitor.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import computeythings.garagemonitor.preferences.ServerPreferences;

/**
 * BroadcastReceiver to receive on boot signal. Starts the service which listens to the currently
 * selected server.
 * <p>
 * Created by bryan on 3/3/18.
 */

public class OnBoot extends BroadcastReceiver {
    private static final String TAG = "BOOT_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()) ||
                Intent.ACTION_REBOOT.equals(intent.getAction()))
            return;

        ServerPreferences prefs = new ServerPreferences(context);

        if(prefs.getSelectedServer() != null)
            context.startService(new Intent(context, FirestoreListenerService.class));
    }
}
