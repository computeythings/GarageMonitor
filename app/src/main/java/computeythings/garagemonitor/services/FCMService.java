package computeythings.garagemonitor.services;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.media.RingtoneManager;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.List;
import java.util.Map;
import java.util.Set;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.preferences.ServerPreferences;

public class FCMService extends FirebaseMessagingService {
    private static final String TAG = "FCMService";
    private static final String NOTIFICATION_CHANNEL =
            "computeythings.garagemonitor.services.FirestoreListener.NOTIFICATIONS";
    private static final String STATE = "STATE";

    /*
        Will be run any time a topic is updated with the server's state
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String sender = remoteMessage.getFrom();
        if(sender == null) // discard if we can't verify sender
            return;
        // sender should be /topics/<refID> so we should start past the last '/'
        sender = sender.substring(sender.lastIndexOf('/') + 1);
        Map<String, String> data = remoteMessage.getData();
        Log.d(TAG, "Received message from " + sender);
        if(remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Message data payload: " + remoteMessage.getData());
            if(!checkApp()) { // should only send notifications if app isn't running
                Set<String> subs = new ServerPreferences(this).getServersFromRef(sender);
                // send a notification for each subscribed server
                for(String subscribedServer : subs) {
                    sendNotification(subscribedServer, data.get(STATE));
                }
            }
        }
    }

    public boolean checkApp(){
        String packageName = getApplicationContext().getPackageName();
        ActivityManager am = (ActivityManager) this
                .getSystemService(ACTIVITY_SERVICE);

        // get the info from the currently running task
        if(am != null) {
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);

            ComponentName componentInfo = taskInfo.get(0).topActivity;
            return componentInfo.getPackageName().equalsIgnoreCase(packageName);
        }
        return false;
    }



    private void sendNotification(String server, String state) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                NOTIFICATION_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(server)
                .setContentText("State changed to: " + state)
                .setVibrate(new long[0])
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        builder.setSound(alarmSound);
        NotificationManagerCompat.from(this).notify(0, builder.build());
    }
}
