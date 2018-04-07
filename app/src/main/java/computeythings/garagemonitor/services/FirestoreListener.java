package computeythings.garagemonitor.services;

import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.Map;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.interfaces.FirestoreUIListener;

public class FirestoreListener {
    private static final String TAG = "FirestoreListener";
    private static final String STATE = "STATE";
    private static final String SERVER_COLLECTION = "servers";
    private static final String NOTIFICATION_CHANNEL =
            "computeythings.garagemonitor.services.FirestoreListener.NOTIFICATIONS";

    private String refId;
    private Context context;
    private DocumentReference firestoreDocument;
    private ListenerRegistration docSubscription;
    private HashSet<String> followers; // list of servers following this document
    private HashSet<FirestoreUIListener> listeners; // list of threads actively listening


    FirestoreListener(String refId, Context context) {
        this.refId = refId;
        this.context = context;
        this.followers = new HashSet<>();
        this.listeners = new HashSet<>();

        this.firestoreDocument = FirebaseFirestore.getInstance()
                .collection(SERVER_COLLECTION)
                .document(refId);
        subscribe();
    }

    /*
        Listen for changes on a Firestore document and store the listener for later removal
     */
    private void subscribe() {
        docSubscription = firestoreDocument.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            boolean initDone = false;

            @Override
            public void onEvent(DocumentSnapshot documentSnapshot, FirebaseFirestoreException e) {
                if (!initDone) {
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
                    Log.d(TAG, "Received data: " + data + " from " + refId);

                    // update active listeners
                    if (listeners.size() > 0) {
                        for (FirestoreUIListener listener : listeners)
                            updateListener(listener, documentSnapshot);
                        // if there is no active listener to the document ref, send a notification
                    } else {
                        for (String server : followers) {
                            sendNotification(server, data.get(STATE).toString());
                        }
                    }
                } else {
                    Log.d(TAG, "Received data: null from " + refId);
                }
            }
        });
    }

    public void unsubscribe() {
        docSubscription.remove();
    }

    /*
        Update all listeners registered to the updated refId
     */
    private void updateListener(FirestoreUIListener listener, DocumentSnapshot documentSnapshot) {
        if (documentSnapshot.getData() == null) {
            Log.e(TAG, "Could not get data from database document.");
            listener.onDataReceived(null);
            return;
        }
        listener.onDataReceived(documentSnapshot.getData().get(STATE).toString());
    }

    private void sendNotification(String server, String state) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context,
                NOTIFICATION_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(server)
                .setContentText("State changed to: " + state)
                .setVibrate(new long[0])
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(alarmSound);
        NotificationManagerCompat.from(context).notify(0, builder.build());
    }


    /*
        Adds a listener to a document which will be updated upon each state change.
        FirestoreUIListeners are intended to be part of the UI and should update the main thread
        whenever changes are detected.
     */
    public void addUIListener(FirestoreUIListener listener) {
        this.listeners.add(listener);
        refreshUI(listener);
    }

    public void refreshUI(final FirestoreUIListener listener) {
        firestoreDocument.get().addOnSuccessListener(new OnSuccessListener<DocumentSnapshot>() {
            @Override
            public void onSuccess(DocumentSnapshot documentSnapshot) {
                updateListener(listener, documentSnapshot);
            }
        });
    }

    /*
        Removes a UI listener from a document. That UI will no longer be updated on doc changes
     */
    public void removeUIListener(FirestoreUIListener listener) {
        if (this.listeners.contains(listener))
            this.listeners.remove(listener);
    }

    /*
        Adds a server to a document which will be referenced when notifications are sent
        in the background.
     */
    public void addServer(String follower) {
        this.followers.add(follower);
    }

    /*
        Removes a server
     */
    public void removeServer(String follower) {
        if (this.followers.contains(follower))
            this.followers.remove(follower);
    }

    public boolean hasFollowers() {
        return this.followers.size() > 0;
    }
}
