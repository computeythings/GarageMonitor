package computeythings.piopener.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessaging;

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
    public static final String NOTIFY_OPEN = "NOTIFY_OPEN";
    public static final String NOTIFY_OPENING = "NOTIFY_OPENING";
    public static final String NOTIFY_CLOSED = "NOTIFY_CLOSED";
    public static final String NOTIFY_CLOSING = "NOTIFY_CLOSING";
    public static final String NOTIFICATION_TIMER = "NOTIFICATION_TIMER";
    public static final String LAST_STATE = "LAST_STATE";
    public static final String LAST_UPDATED = "LAST_UPDATED";
    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_OPENING = "OPENING";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_CLOSING = "CLOSING";

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
            serverInfo.put(LAST_STATE, state);
            serverInfo.put(LAST_UPDATED, updateTime);
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
    public boolean setNotifications(String server, Set<String> monitoredStates,
                                    long notificationTimer) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFERENCES + SERVERS,
                Context.MODE_PRIVATE);
        JSONObject serverInfo;
        try {
            serverInfo = new JSONObject(prefs.getString(server, ""));
            serverInfo.put(NOTIFY_OPEN, monitoredStates.contains(STATE_OPEN));
            serverInfo.put(NOTIFY_OPENING, monitoredStates.contains(STATE_OPENING));
            serverInfo.put(NOTIFY_CLOSED, monitoredStates.contains(STATE_CLOSED));
            serverInfo.put(NOTIFY_CLOSING, monitoredStates.contains(STATE_CLOSING));
            serverInfo.put(NOTIFICATION_TIMER, notificationTimer);
        } catch (JSONException e) {
            Log.e(TAG, "Received bad JSON info for " + server);
            return false;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(server, serverInfo.toString());
        return editor.commit();
    }

    /*
        Whether or not user should receive alerts from this server
     */
    public boolean notificationsEnabled(String server, String state) {
        HashMap<String, String> info = getServerInfo(server);
        if (info == null)
            return false;
        switch (state) {
            case STATE_OPEN:
                return Boolean.valueOf(info.get(NOTIFY_OPEN));
            case STATE_OPENING:
                return Boolean.valueOf(info.get(NOTIFY_OPENING));
            case STATE_CLOSED:
                return Boolean.valueOf(info.get(NOTIFY_CLOSED));
            case STATE_CLOSING:
                return Boolean.valueOf(info.get(NOTIFY_CLOSING));
            // If we run into a non-specified state,
            // we should probably alert the user of something wonky going on
            default:
                return true;
        }
    }

    /*
        Returns the delay timer for the server or 0 if there is none
     */
    public long notificationDelay(String server) {
        HashMap<String, String> info = getServerInfo(server);
        String delay = info.get(NOTIFICATION_TIMER);

        return delay == null ? 0L : Long.parseLong(delay) * 60000;
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
        String refID = getServerInfo(server).get(SERVER_REFID);
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
