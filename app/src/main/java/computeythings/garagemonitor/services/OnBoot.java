package computeythings.garagemonitor.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import computeythings.garagemonitor.preferences.ServerPreferences;

/**
 * Created by bryan on 3/3/18.
 */

public class OnBoot extends BroadcastReceiver {
    private static final String TAG = "BOOT_SERVICE";

    @Override
    public void onReceive(Context context, Intent intent) {
        ServerPreferences prefs = new ServerPreferences(context);
        Intent startServiceIntent = new Intent(context, TCPSocketService.class);

        if(prefs.getSelectedServer() != null) {
            try {
                JSONObject server = new JSONObject(prefs.getServerInfo(
                        prefs.getSelectedServer()));
                startServiceIntent.putExtra(TCPSocketService.SERVER_ADDRESS, server.getString(
                        ServerPreferences.SERVER_ADDRESS));
                startServiceIntent.putExtra(TCPSocketService.API_KEY, server.getString(
                        ServerPreferences.SERVER_API_KEY));
                startServiceIntent.putExtra(TCPSocketService.PORT_NUMBER, server.getInt(
                        ServerPreferences.SERVER_PORT));
                startServiceIntent.putExtra(TCPSocketService.CERT_ID, server.getString(
                        ServerPreferences.SERVER_CERT));
            } catch (JSONException e) {
                Log.e(TAG, "Invalid server settings");
                e.printStackTrace();
                return;
            }
        }

        context.startService(startServiceIntent);
    }
}
