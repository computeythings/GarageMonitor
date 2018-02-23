package computeythings.garagemonitor.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

import computeythings.garagemonitor.ui.AddServerDialog;

/**
 * Class used to interact with the apps saved preferences
 * <p>
 * Created by bryan on 2/11/18.
 */

public class ServerPreferences {
    private static final String TAG = "SERVER_PREFS";
    private static final String PREFERENCES_NAME = "computeythings.garagemonitor.PREFERENCES";
    private static final String SELECTED_SERVER = "SELECTED_SERVER";
    private static final String SERVER_LIST = "SERVER_LIST";
    public static final String SERVER_NAME = "NAME";
    public static final String SERVER_ADDRESS = "ADDRESS";
    public static final String SERVER_API_KEY = "API_KEY";
    public static final String SERVER_PORT = "PORT";
    public static final String SERVER_CERT = "CERT_LOCATION";

    private SharedPreferences mPrefs;

    public ServerPreferences(Context context) {
        mPrefs = context.getSharedPreferences(PREFERENCES_NAME, 0);
    }

    /*
        Adds a new server to the list saved in preferences
     */
    public boolean addServer(String name, String address, String apikey, int port,
                             String certLocation) {
        JSONObject json = new JSONObject();
        try {
            json.put(SERVER_NAME, name);
            json.put(SERVER_ADDRESS, address);
            json.put(SERVER_API_KEY, apikey);
            json.put(SERVER_PORT, port);
            if(!certLocation.equals(AddServerDialog.USE_CURRENT)) // don't change cert location
                json.put(SERVER_CERT, certLocation);
        } catch (JSONException e) {
            Log.e(TAG, "Unexpected JSON error");
            e.printStackTrace();
            return false;
        }

        Set<String> serverList = getServerList();
        serverList.add(name); // Add this server to the known list of servers

        // Write new values to preferences
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(name, json.toString());
        editor.putStringSet(SERVER_LIST, serverList);
        return editor.commit();
    }

    public Set<String> getServerList() {
        return mPrefs.getStringSet(SERVER_LIST, new HashSet<String>());
    }

    public boolean removeServer(String serverName) {
        Set<String> serverList = getServerList();

        if (!serverList.contains(serverName))
            return true; // return true if the list doesn't already contain the server


        serverList.remove(serverName); // remove server from server list
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.remove(serverName); // remove server key and its values
        editor.putStringSet(SERVER_LIST, serverList); // write update server list

        // If the server being removed is the current server, set current selected to null
        if(mPrefs.getString(SELECTED_SERVER, "").equals(serverName))
            editor.putString(SELECTED_SERVER, null);

        return editor.commit();
    }

    public String getServerInfo(String serverName) {
        return mPrefs.getString(serverName, null);
    }

    public boolean setSelectedServer(String serverName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(SELECTED_SERVER, serverName);
        return editor.commit();
    }

    public String getSelectedServer() {
        return mPrefs.getString(SELECTED_SERVER, null);
    }
}
