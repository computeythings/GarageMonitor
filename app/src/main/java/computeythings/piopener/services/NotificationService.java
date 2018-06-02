package computeythings.piopener.services;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import computeythings.piopener.R;
import computeythings.piopener.preferences.ServerPreferences;

public class NotificationService extends Service {
    private static final int SUMMARY_ID = 0;
    public static final String SERVER = "SERVER";
    public static final String SENT_TIME = "SENT_TIME";
    public static final String STATE = "STATE";
    private static final String CLOSED = "CLOSED";
    private static final String NOTIFICATION_CHANNEL =
            "computeythings.garagemonitor.services.NotificationService.NOTIFICATIONS";
    private static final String NOTIFICATION_GROUP_PREFIX =
            "computeythings.garagemonitor.services.NotificationService.NOTIFICATIONS.";
    private static final String TAG = "NotificationService";

    private static int notification_id = 1;
    private ServerPreferences prefs;
    private HashMap<String, Runnable> notifications;
    private Handler notificationSender;
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Notification service created");
        this.prefs = new ServerPreferences(this);
        this.notifications = new HashMap<>();
        this.notificationSender = new Handler();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null)
            return START_STICKY;

        String server = intent.getStringExtra(SERVER);
        String state = intent.getStringExtra(STATE);
        long sentTime = intent.getLongExtra(SENT_TIME, 0L);
        long delay = prefs.notificationDelay(server);

        // always send an initial notification if enabled
        if (prefs.notificationsEnabled(server, state)) {
            sendNotification(server, "State changed to: " + state, sentTime);
        }

        if (CLOSED.equals(state)) {
            // clear timer once door has been closed
            Runnable notification = notifications.put(server, null);
            if (notification != null)
                notificationSender.removeCallbacks(notification);
            // start notification timer only once the door isn't closed and if a delay was set
        } else if (delay > 0) {
            if (startNotificationTimer(server, sentTime, delay))
                Log.d(TAG, "Monitoring garage opener " + server);
            else
                Log.d(TAG, "Running notification task already exists for " + server);
        }
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Notification service destroyed");
        // service is kill. Cancel all running tasks.
        for (Runnable notification : notifications.values()) {
            notificationSender.removeCallbacks(notification);
        }
        super.onDestroy();
    }

    private boolean startNotificationTimer(String server, long startTime, long delay) {
        if(notifications.get(server) != null) // make sure only one timer is active at once
            return false;

        Runnable notification = new NotificationTimer(server, startTime);
        notifications.put(server, notification);
        return notificationSender.postDelayed(notification, delay);
    }

    /*
        Returns true if the app is active and running in the foreground.
     */
    public boolean checkApp() {
        String packageName = getApplicationContext().getPackageName();
        ActivityManager am = (ActivityManager) this
                .getSystemService(ACTIVITY_SERVICE);

        // get the info from the currently running task
        if (am != null) {
            List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);

            ComponentName componentInfo = taskInfo.get(0).topActivity;
            return componentInfo.getPackageName().equalsIgnoreCase(packageName);
        }
        return false;
    }

    /*
        Sends a push notification if the app is not active unless @param checkOverride is specified
     */
    private void sendNotification(String server, String message, long sentTime,
                                  boolean checkOverride) {
        if (!checkOverride && checkApp()) // if the app is active in the foreground, don't send a notification
            return;

        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        Notification notification =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(server)
                .setContentText(message)
                .setSound(alarmSound)
                .setVibrate(new long[0])
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(NOTIFICATION_GROUP_PREFIX + server)
                .setAutoCancel(true)
                .build();
        Notification summary =
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setStyle(new NotificationCompat.InboxStyle())
                        .setGroup(NOTIFICATION_GROUP_PREFIX + server)
                        .setGroupSummary(true)
                        .build();
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        notificationManager.notify(notification_id++, notification);
        notificationManager.notify(SUMMARY_ID, summary);
    }

    private void sendNotification(String server, String message, long sentTime) {
        sendNotification(server, message, sentTime, false);
    }


    private class NotificationTimer implements Runnable {
        private String server;
        private long timeOpened;

        NotificationTimer(String server, long timeOpened) {
            this.server = server;
            this.timeOpened = timeOpened;
        }

        @Override
        public void run() {
            long delay = prefs.notificationDelay(server);

            // if delay is <= 0 then that means timed alerts have been disabled
            if (delay > 0) {
                long currentTime = Calendar.getInstance().getTimeInMillis();
                if (currentTime - timeOpened > delay) {
                    // always alert - even when app is active
                    sendNotification(server, "Your garage has been open for over" +
                                    readTime(currentTime - timeOpened) + "!",
                            Calendar.getInstance().getTimeInMillis(), true);
                    notificationSender.postDelayed(this, delay);
                }
            }
        }

        private String readTime(long milliseconds) {
            long minutes = milliseconds / 60000;
            String humanReadable = "";
            long hours = (minutes / 60) % 24;
            long days = (minutes / (60 * 24)) % 365;

            if (days > 0) {
                humanReadable += " " + days + " day";
                if (days > 1)
                    humanReadable += "s";
            }
            if (hours > 0) {
                humanReadable += " " + hours + " hour";
                if (hours > 1)
                    humanReadable += "s";
            }
            if (minutes > 0) {
                humanReadable += " " + minutes + " minute";
                if (minutes > 1)
                    humanReadable += "s";
            }
            return humanReadable;
        }
    }
}
