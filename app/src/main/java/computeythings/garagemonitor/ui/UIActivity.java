package computeythings.garagemonitor.ui;

import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import computeythings.garagemonitor.R;

public class UIActivity extends AppCompatActivity implements AddServerDialog.OnServerAddedListener {
    private static final String TAG = "MAIN_ACTIVITY";
    UIFragment mUIFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_container);
        mUIFragment = (UIFragment) getSupportFragmentManager().findFragmentById(R.id.ui_fragment);
    }

    @Override
    public void onBackPressed() {
        try {
            DrawerLayout drawer = mUIFragment.mDrawer;
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

    @Override
    public void onServerAdded(boolean isFirstServer) {
        mUIFragment.updateServerList();
    }
}
