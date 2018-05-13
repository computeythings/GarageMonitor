package computeythings.piopener.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Class used to interact with the apps saved preferences
 * <p>
 * Created by bryan on 2/11/18.
 */

public class ServerPreferences {
    private static final String TAG = "SERVER_PREFS";
    private static final String PREFERENCES = "computeythings.garagemonitor.PREFERENCES";
    private static final String SELECTED_SERVER = ".SELECTED_SERVER";
    private static final String SERVERS = ".SERVERS";
    private static final String REFIDS = ".REFIDS";
    private static final String SELECTED = "SELECTED";
    public static final String SERVER_NAME = "NAME";
    public static final String SERVER_ADDRESS = "ADDRESS";
    public static final String SERVER_API_KEY = "API_KEY";
    public static final String SERVER_PORT = "PORT";
    public static final String SERVER_CERT = "CERT_LOCATION";
    public static final String SERVER_REFID = "REFID";
    public static final String NOTIFICATIONS = "NOTIFICATIONS";
    public static final String NOTIFICATION_TIMER = "NOTIFICATION_TIMER";
    public static final String LAST_STATE = "LAST_STATE";
    public static final String LAST_UPDATED = "LAST_UPDATED";

    private Context mContext;

    public ServerPreferences(Context context) {
        mContext = context;
    }

    /*
        Adds a new server to the list saved in preferences
     */
    public boolean addServer(String name, String address, String apikey, int port,
                             String certLocation) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
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

        // write new values to preferences
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(name, json.toString());
        return editor.commit();
    }

    public boolean updateServer(String server, String state, long updateTime) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        JSONObject serverInfo;
        try {
            serverInfo = new JSONObject(prefs.getString(server, ""));
            if (!serverInfo.has(LAST_STATE) || !serverInfo.getString(LAST_STATE).equals(state)) {
                serverInfo.put(LAST_STATE, state);
                serverInfo.put(LAST_UPDATED, updateTime);
            }
        } catch (JSONException e) {
            Log.e(TAG, "Received bad JSON info for " + server);
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(server, serverInfo.toString());
        return editor.commit();
    }

    /*
        Updates notification info for a given server
     */
    public void setNotifications(String server, Set<String> monitoredStates,
                                 long notificationTimer) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        JSONObject serverInfo;
        try {
            serverInfo = new JSONObject(prefs.getString(server, ""));
            serverInfo.put(NOTIFICATIONS, monitoredStates.toString());
            serverInfo.put(NOTIFICATION_TIMER, notificationTimer);
        } catch (JSONException e) {
            Log.e(TAG, "Received bad JSON info for " + server);
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(server, serverInfo.toString());
        editor.apply();
    }

    /*
        Whether or not user should receive alerts from this server
     */
    public List<String> notificationsEnabled(String server) {
        HashMap<String, String> info = getServerInfo(server);
        if (info == null || !info.containsKey(NOTIFICATIONS))
            return null;
        String listString = info.get(NOTIFICATIONS);
        // remove brackets
        listString = listString.substring(1,listString.length() - 1);
        return Arrays.asList(listString.split(", "));
    }

    public boolean setServerRefId(String server, String refID) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        JSONObject json;
        try {
            json = new JSONObject(prefs.getString(server, ""));
            if (json.has(SERVER_REFID) && json.getString(SERVER_REFID).equals(refID))
                return true; // no need to do anymore since the same ID is already stored.
            // subscribe to the FCM topic to received updates
            FirebaseMessaging.getInstance().subscribeToTopic(refID);
            json.put(SERVER_REFID, refID);
        } catch (JSONException e) {
            Log.e(TAG, "Could not parse info for " + server);
            e.printStackTrace();
            return false;
        }
        // subscribe to the server topic to receive FCM updates
        FirebaseMessaging.getInstance().subscribeToTopic(refID);
        // add server to the subscribed servers under refID in shared prefs
        SharedPreferences refPrefs = mContext.getSharedPreferences(PREFERENCES + REFIDS,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor refEditor = refPrefs.edit();
        Set<String> subs = refPrefs.getStringSet(refID, new HashSet<String>());
        subs.add(server);
        refEditor.putStringSet(refID, subs);

        // save refID to server properties
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(server, json.toString());
        return editor.commit() && refEditor.commit();
    }

    public String getSelectedServer() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SELECTED_SERVER,
                Context.MODE_PRIVATE);
        return prefs.getString(SELECTED, null);
    }

    /*
        Gets server info stored as JSON and converts it to a HashMap
     */
    public HashMap<String, String> getServerInfo(String server) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        HashMap<String, String> serverList = new HashMap<>();
        try {
            JSONObject serverInfo = new JSONObject(prefs.getString(server, ""));
            Iterator<String> it = serverInfo.keys();
            String key;
            while (it.hasNext()) {
                key = it.next();
                serverList.put(key, serverInfo.getString(key));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to get info for " + server);
            return null;
        }
        return serverList;
    }

    public boolean setSelectedServer(String server) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SELECTED_SERVER,
                Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SELECTED, server);
        return editor.commit();
    }

    public boolean removeServer(String server) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);

        if (!prefs.contains(server))
            return true; // return true if the list doesn't already contain the server

        // remove the server from its subscribed upstream document
        String refID = getServerInfo(server).get(server);
        if (refID != null) {
            SharedPreferences refPrefs = mContext.getSharedPreferences(PREFERENCES + REFIDS,
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = refPrefs.edit();
            Set<String> subs = refPrefs.getStringSet(refID, new HashSet<String>());
            subs.remove(server);
            if (subs.size() > 0)
                editor.putStringSet(refID, subs);
            else {// if the document no longer has any subs, remove it from preferences
                editor.remove(refID);
                FirebaseMessaging.getInstance().unsubscribeFromTopic(refID);
            }
            editor.apply();
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(server); // remove server key and its values

        // if the server being removed is the current server, set current selected to null
        if (getSelectedServer().equals(server))
            setSelectedServer(null);

        return editor.commit();
    }

    public Set<String> getServersFromRef(String refID) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + REFIDS,
                Context.MODE_PRIVATE);
        return prefs.getStringSet(refID, new HashSet<String>());
    }

    public Set<String> getServerList() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getAll().keySet());
    }

    public Set<String> getUpstreamServerRefs() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + REFIDS,
                Context.MODE_PRIVATE);
        return new HashSet<>(prefs.getAll().keySet());
    }
}
