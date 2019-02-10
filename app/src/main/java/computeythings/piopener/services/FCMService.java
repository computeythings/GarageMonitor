package computeythings.piopener.services;

import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
import java.util.Set;

import computeythings.piopener.preferences.ServerPreferences;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String STATE = "STATE";
    public static final String SERVER_UPDATE_RECEIVED =
            "computeythings.garagemonitor.services.FCMService.SERVER_UPDATE";

    /*
        Will be run any time a topic is updated with the server's state
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String sender = remoteMessage.getFrom();
        Log.d(TAG, "Received message from " + sender);
        if (sender == null) // discard if we can't verify sender
            return;
        // sender should be /topics/<refID> so we should start past the last '/'
        sender = sender.substring(sender.lastIndexOf('/') + 1);
        Map<String, String> data = remoteMessage.getData();
        if (remoteMessage.getData().size() > 0) {
            ServerPreferences prefs = new ServerPreferences(this);
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            Set<String> subs = prefs.getServersFromRef(sender);

            String state;
            // update each subscribed server
            for (String subscribedServer : subs) {
                state = data.get(STATE);
                long sent = remoteMessage.getSentTime();
                // update stored values of server
                prefs.updateServer(subscribedServer, state, sent);

                // send intent for push notification
                Intent notificationIntent = new Intent(this, NotificationService.class);
                notificationIntent.putExtra(NotificationService.SERVER, subscribedServer);
                notificationIntent.putExtra(NotificationService.STATE, state);
                notificationIntent.putExtra(NotificationService.SENT_TIME, sent);
                startService(notificationIntent);
            }

            // broadcast that an update was received on this refID
            LocalBroadcastManager broadcaster = LocalBroadcastManager.getInstance(this);
            Intent update = new Intent();
            update.setAction(SERVER_UPDATE_RECEIVED);
            update.putExtra(ServerPreferences.SERVER_REFID, sender);
            broadcaster.sendBroadcast(update);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.e("NEW TOKEN:", token);
    }
}
