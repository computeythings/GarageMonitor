package computeythings.garagemonitor.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

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
    public static final String SERVER_REFID = "SERVER_REFID";

    private final SharedPreferences mPrefs;

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
            json.put(SERVER_CERT, certLocation);
        } catch (JSONException e) {
            Log.e(TAG, "Unexpected JSON error");
            e.printStackTrace();
            return false;
        }

        Set<String> serverList = getServerList();
        serverList.add(name); // add this server to the known list of servers

        // write new values to preferences
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(name, json.toString());
        editor.putStringSet(SERVER_LIST, serverList);
        return editor.commit();
    }

    public boolean setServerRefid(String serverName, String refId) {
        JSONObject json;
        try {
            json = new JSONObject(mPrefs.getString(serverName, ""));
            if(json.has(SERVER_REFID) && json.getString(SERVER_REFID).equals(refId))
                return true; // no need to do anymore since the same ID is already stored.
            json.put(SERVER_REFID, refId);
        } catch (JSONException e) {
            Log.d(TAG, "Could not parse info for " + serverName);
            e.printStackTrace();
            return false;
        }
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(serverName, json.toString());
        return editor.commit();
    }

    public String getSelectedServer() {
        return mPrefs.getString(SELECTED_SERVER, null);
    }

    public Set<String> getServerList() {
        return mPrefs.getStringSet(SERVER_LIST, new HashSet<String>());
    }

    /*
        Gets server info stored as JSON and converts it to a HashMap
     */
    public HashMap<String, String> getServerInfo(String serverName) {
        HashMap<String, String> serverList = new HashMap<>();
        try {
            JSONObject serverInfo = new JSONObject(mPrefs.getString(serverName, ""));
            Iterator<String> it = serverInfo.keys();
            String key;
            while (it.hasNext()) {
                key = it.next();
                serverList.put(key, serverInfo.getString(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get info for " + serverName);
            return null;
        }
        return serverList;
    }

    public boolean setSelectedServer(String serverName) {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString(SELECTED_SERVER, serverName);
        return editor.commit();
    }

    public boolean removeServer(String serverName) {
        Set<String> serverList = getServerList();

        if (!serverList.contains(serverName))
            return true; // return true if the list doesn't already contain the server


        serverList.remove(serverName); // remove server from server list
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.remove(serverName); // remove server key and its values
        editor.putStringSet(SERVER_LIST, serverList); // write update server list

        // if the server being removed is the current server, set current selected to null
        if (mPrefs.getString(SELECTED_SERVER, "").equals(serverName))
            editor.putString(SELECTED_SERVER, null);

        return editor.commit();
    }
}
