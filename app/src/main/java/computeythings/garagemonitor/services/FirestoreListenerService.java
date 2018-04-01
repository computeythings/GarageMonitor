package computeythings.garagemonitor.services;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

public class FirestoreListenerService extends Service {
    private static final String TAG = "FirestoreListener";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "New socket service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // This service should always be running
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
