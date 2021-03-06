package computeythings.piopener.ui;

import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import computeythings.piopener.R;

/**
 * Activity responsible for fragment running and communication. Responds to callbacks from
 * AddServerDialog when new servers are updated and relays those callbacks to the UI fragment.
 * <p>
 * Created by bryan on 2/9/18.
 */

public class UIActivity extends AppCompatActivity implements AddServerDialog.OnServerListChangeListener {
    private static final String TAG = "UI_ACTIVITY";
    // only one of this activity should be running at a time
    public static boolean isRunning = false; // IMPORTANT for proper notification updates

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_container);
    }

    @Override
    public void onStart() {
        super.onStart();
        isRunning = true;
    }

    /*
        If the app drawer is open, close that with back button.
     */
    @Override
    public void onBackPressed() {
        try {
            StatusViewFragment ui = (StatusViewFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.ui_fragment);
            DrawerLayout drawer = ui.getDrawerLayout();
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                super.onBackPressed();
            }
        } catch (NullPointerException e) {
            Log.e(TAG, "Attempting to use null view");
            e.printStackTrace();
            super.onBackPressed();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        isRunning = false;
    }

    /*
        Alert the UI fragment that a new server has been added
     */
    @Override
    public void onServerModify(String server) {
        if (server == null) {
            Log.e(TAG, "Attempting to add null server");
            return;
        }
        StatusViewFragment ui = (StatusViewFragment) getSupportFragmentManager().findFragmentById(R.id.ui_fragment);
        ui.serverModified(server);
    }

    @Override
    public void onServerDeleted(String server) {
        if (server == null) {
            Log.e(TAG, "Attempting to delete null server");
            return;
        }
        StatusViewFragment ui = (StatusViewFragment) getSupportFragmentManager().findFragmentById(R.id.ui_fragment);
        ui.serverDeleted(server);
    }
}
