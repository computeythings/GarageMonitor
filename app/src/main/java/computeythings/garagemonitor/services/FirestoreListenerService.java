package computeythings.garagemonitor.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;

import computeythings.garagemonitor.interfaces.FirestoreUIListener;
import computeythings.garagemonitor.preferences.ServerPreferences;

public class FirestoreListenerService extends IntentService {
    private static final String TAG = "FirestoreListener";
    public static final String FOLLOW_SERVER =
            "computeythings.garagemonitor.services.FirestoreListenerService.FOLLOW_SERVER";
    public static final String UNFOLLOW_SERVER =
            "computeythings.garagemonitor.services.FirestoreListenerService.UNFOLLOW_SERVER";
    private HashMap<String, FirestoreListener> mDocumentListeners;
    private HashMap<String, FirestoreUIListener> mListenerQueue;
    private ServiceBinder mBinder;

    public FirestoreListenerService() {
        super("FirestoreListenerService");
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "New Firestore listener service created");
        super.onCreate();
        mDocumentListeners = new HashMap<>();
        mListenerQueue = new HashMap<>();
        mBinder = new ServiceBinder();

        // subscribe to all known servers
        ServerPreferences prefs = new ServerPreferences(this);
        for (String server : prefs.getServerList()) {
            String refId = prefs.getServerInfo(server).get(ServerPreferences.SERVER_REFID);
            if (refId != null) {
                addServer(server, refId);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        onHandleIntent(intent);
        return START_STICKY; // this service should always be running
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /*
        Service will handle requests to follow/unfollow server database documents given
        a document reference ID.
     */
    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null || intent.getAction() == null)
            return;

        String refId = intent.getStringExtra(ServerPreferences.SERVER_REFID);
        String server = intent.getStringExtra(ServerPreferences.SERVER_NAME);
        if (refId == null)
            return;

        switch (intent.getAction()) {
            case FOLLOW_SERVER:
                addServer(server, refId);
                break;
            case UNFOLLOW_SERVER:
                removeServer(server, refId);
        }
    }

    /*
        Add a server reference to a Firestore listener.
        Creates the listener if it does not already exist
     */
    private void addServer(String server, String refId) {
        if (!mDocumentListeners.containsKey(refId)) {
            Log.i(TAG, "Adding server listener for " + refId);
            mDocumentListeners.put(refId, new FirestoreListener(refId, this));
        }
        mDocumentListeners.get(refId).addServer(server);
        // If this server has a listener queued, add it and remove from queue
        if(mListenerQueue.containsKey(server))
            mDocumentListeners.get(refId).addUIListener(mListenerQueue.remove(server));
    }

    /*
        Remove a server from a Firestore listener. If all servers are removed from a listener, the
        document's onSnapshotListener will be removed and the listener removed from the service.
     */
    private void removeServer(String server, String refId) {
        FirestoreListener listener = mDocumentListeners.get(refId);
        listener.removeServer(server);
        if (listener.hasFollowers())
            listener.unsubscribe();
        mDocumentListeners.remove(refId);
    }

    /*
        Add UI listener to be updated on document changes
     */
    private void subscribeListener(FirestoreUIListener uiListener, String ref) {
        if(mDocumentListeners.containsKey(ref))
            mDocumentListeners.get(ref).addUIListener(uiListener);
        else
            queueListener(uiListener, ref);
    }

    /*
        If a listener is added with a server name it's because no document reference ID exists in
        preferences. Add the listener to a queue which is queried upon each server added.
     */
    private void queueListener(FirestoreUIListener uiListener, String server) {
        mListenerQueue.put(server, uiListener);
    }

    /*
        Get latest data from Firestore server and send to UI client
     */
    private void refreshListener(FirestoreUIListener uiListener, String refId) {
        if(mDocumentListeners.containsKey(refId))
            mDocumentListeners.get(refId).refreshUI(uiListener);
    }

    /*
        Remove a UI listener
     */
    private void unsubscribeListener(FirestoreUIListener uiListener, String refId) {
        if(mDocumentListeners.containsKey(refId))
            mDocumentListeners.get(refId).removeUIListener(uiListener);
    }

    /*
        Interface to allow UI to bind itself and listen for data changes
     */
    public class ServiceBinder extends Binder {
        /*
            Adds listener with the ref ID as the value. If a ref ID does not exist, the server name
            should be used as it will be swapped out when one is available.
         */
        public void addDataListener(FirestoreUIListener listener, String ref) {
            subscribeListener(listener, ref);
        }

        public void removeDataListener(FirestoreUIListener listener, String refId) {
            unsubscribeListener(listener, refId);
        }

        public void refreshData(final FirestoreUIListener listener, String refId) {
            refreshListener(listener, refId);
        }

        public void unsubscribe(FirestoreUIListener listener, String refId) {
            unsubscribeListener(listener, refId);
        }
    }

    /*
        Unsubscribe from all database listeners when this object is destroyed
     */
    @Override
    public void onDestroy() {
        for (FirestoreListener listener : mDocumentListeners.values()) {
            listener.unsubscribe();
        }
        super.onDestroy();
    }
}
