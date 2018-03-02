package computeythings.garagemonitor.ui;

import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import computeythings.garagemonitor.R;

/**
 * Activity responsible for fragment running and communication. Responds to callbacks from
 * AddServerDialog when new servers are updated and relays those callbacks to the UI fragment.
 *
 * Created by bryan on 2/9/18.
 */

public class UIActivity extends AppCompatActivity implements AddServerDialog.OnServerListChangeListener {
    private static final String TAG = "MAIN_ACTIVITY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_container);

    }

    // If the app drawer is open, close that with back button.
    @Override
    public void onBackPressed() {
        try {
            UIFragment ui = (UIFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.ui_fragment);
            DrawerLayout drawer = ui.getDrawerLayout();
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                super.onBackPressed();
            }
        } catch (NullPointerException e) {
            Log.d(TAG, "Attempting to use null view");
            e.printStackTrace();
            super.onBackPressed();
        }
    }

    // Alert the UI fragment that a new server has been added
    @Override
    public void onServerAdded(boolean isFirstServer) {
        UIFragment ui = (UIFragment) getSupportFragmentManager().findFragmentById(R.id.ui_fragment);
        ui.updateServerList(isFirstServer);
    }

    @Override
    public void onServerDeleted() {
        UIFragment ui = (UIFragment) getSupportFragmentManager().findFragmentById(R.id.ui_fragment);
        ui.serverDeleted();
    }
}
