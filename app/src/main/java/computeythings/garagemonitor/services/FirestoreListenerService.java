package computeythings.garagemonitor.services;

import android.app.IntentService;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.interfaces.FirebaseDataListener;
import computeythings.garagemonitor.preferences.ServerPreferences;

public class FirestoreListenerService extends IntentService {
    private static final String TAG = "FirestoreListener";
    private static final String SERVER_COLLECTION = "servers";
    private static final String NOTIFICATION_CHANNEL =
            "computeythings.garagemonitor.services.FirestoreListenerService.NOTIFICATIONS";
    public static final String STATE = "STATE";
    public static final String FOLLOW_SERVER =
            "computeythings.garagemonitor.services.FirestoreListenerService.FOLLOW_SERVER";
    public static final String UNFOLLOW_SERVER =
            "computeythings.garagemonitor.services.FirestoreListenerService.UNFOLLOW_SERVER";

    private FirebaseFirestore mDatabase;
    private HashMap<FirebaseDataListener, String> mDataListeners;
    private HashMap<String, ListenerRegistration> mSubscribed;
    private ServiceBinder mBinder;

    public FirestoreListenerService() {
        super("FirestoreListenerService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mDataListeners = new HashMap<>();
        mSubscribed = new HashMap<>();
        mBinder = new ServiceBinder();
        mDatabase = FirebaseFirestore.getInstance();

        // subscribe to all known servers
        ServerPreferences prefs = new ServerPreferences(this);
        HashMap<String, String> serverInfo;
        for (String server : prefs.getServerList()) {
            serverInfo = prefs.getServerInfo(server);
            if (serverInfo.containsKey(ServerPreferences.SERVER_REFID)) {
                subscribeTo(serverInfo.get(ServerPreferences.SERVER_REFID));
            }
        }


        Log.d(TAG, "New Firestore listener service created");
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
                // swap out server name with ref ID if it exists
                if (mDataListeners.containsValue(server)) {
                    for(FirebaseDataListener listener : mDataListeners.keySet()) {
                        if(mDataListeners.get(listener).equals(server)) {
                            mDataListeners.put(listener, refId);
                            mBinder.refreshData(listener);
                        }
                    }
                }
                subscribeTo(refId);
                break;
            case UNFOLLOW_SERVER:
                unsubscribeFrom(refId);
        }
    }

    /*
        Listen for changes on a Firestore document and store the listener for later removal
     */
    private void subscribeTo(final String refId) {
        if (mSubscribed.containsKey(refId))
            return; // don't subscribe to a document twice

        final DocumentReference docRef = mDatabase.collection(SERVER_COLLECTION).document(refId);
        ListenerRegistration subscription = docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            boolean initDone = false;
            @Override
            public void onEvent(DocumentSnapshot documentSnapshot, FirebaseFirestoreException e) {
                if(!initDone) {
                    // disable initial query since it causes notifications on service start
                    initDone = true;
                    return;
                }
                if (e != null) {
                    Log.w(TAG, "Failed to listen on " + refId);
                    e.printStackTrace();
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    Map<String, Object> data = documentSnapshot.getData();
                    Log.d(TAG, "Received data: " + data);
                    updateListener(refId, data);
                    // if there is no active subscriber to the document ref, send a notification
                    if (!mDataListeners.containsValue(refId)) {
                        sendNotification(data.get(STATE).toString());
                    }
                } else {
                    Log.d(TAG, "Received data: null");
                }
            }
        });
        mSubscribed.put(refId, subscription);
    }

    private void unsubscribeFrom(final String refId) {
        if (!mSubscribed.containsKey(refId))
            return; // obviously nothing to do if we're not already subscribed
        mSubscribed.get(refId).remove(); // remove listener for snapshot changes
        mSubscribed.remove(refId); // remove listener from list of subscribed
    }

    /*
        Update all listeners registered to the updated refId
     */
    private void updateListener(String refId, Map<String, Object> data) {
        if(mDataListeners.containsValue(refId)) {
            for(FirebaseDataListener listener : mDataListeners.keySet()) {
                if(mDataListeners.get(listener).equals(refId))
                    listener.onDataReceived(data);
            }
        }
    }

    private void sendNotification(String state) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                NOTIFICATION_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Garage Door") // TODO: include server name
                .setContentText("State changed to: " + state)
                .setVibrate(new long[0])
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(alarmSound);
        NotificationManagerCompat.from(this).notify(0, builder.build());
    }

    /*
        Interface to allow UI to bind itself and listen for data changes
     */
    public class ServiceBinder extends Binder {
        /*
            Adds listener with the ref ID as the value. If a ref ID does not exist, the server name
            should be used as it will be swapped out when one is available.
         */
        public void addDataListener(String ref, FirebaseDataListener listener) {
            mDataListeners.put(listener, ref);
        }

        public void removeDataListener(FirebaseDataListener listener) {
            mDataListeners.remove(listener);
        }
        public void refreshData(final FirebaseDataListener listener) {
            mDatabase.collection(SERVER_COLLECTION).document(mDataListeners.get(listener)).get()
                    .addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
                        @Override
                        public void onSuccess(DocumentSnapshot documentSnapshot) {
                            listener.onDataReceived(documentSnapshot.getData());
                        }
                    });
        }
        public void unsubscribe(FirebaseDataListener listener) {
            String listenerRef = mDataListeners.get(listener);
            unsubscribeFrom(listenerRef);
        }
    }

    /*
        Unsubscribe from all database listeners when this object is destroyed
     */
    @Override
    public void onDestroy() {
        for (String listener : mSubscribed.keySet()) {
            mSubscribed.get(listener).remove();
        }
        super.onDestroy();
    }
}
