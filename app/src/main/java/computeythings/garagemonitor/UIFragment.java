package computeythings.garagemonitor;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.Fragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by bryan on 2/9/18.
 */

public class UIFragment extends Fragment
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "UI_Fragment";
    private Context mContext;
    private View mParentView;
    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private boolean mSocketBound;
    private ServiceConnection mConnection;
    private String mServerName;
    private String mAPIKey;
    private int mCertId;
    private int mServerPort = 4444;

    protected DrawerLayout mDrawer;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
    }

    @Override
    public void onResume() {
        super.onResume();
        //TODO: pull server settings from settings file
        mServerName = "";
        mAPIKey = "";
        mCertId = R.raw.sslcrt;
        mServerPort = 4444;

        mConnection = new TCPServiceConnection();
        // Create and bind a socket service based on currently selected server
        mContext.bindService(getServerFromSettings(), mConnection, Context.BIND_AUTO_CREATE);
        // Prepare to receive updates from this service
        LocalBroadcastManager.getInstance(mContext).registerReceiver((mDataReceiver),
                new IntentFilter(TCPSocketService.DATA_RECEIVED)
        );
    }

    /*
        Create the intent to start the server from the selected server in the settings
     */
    private Intent getServerFromSettings() {
        Intent intent = new Intent(mContext, TCPSocketService.class);
        intent.putExtra(TCPSocketService.SERVER_NAME, mServerName);
        intent.putExtra(TCPSocketService.API_KEY, mAPIKey);
        intent.putExtra(TCPSocketService.PORT_NUMBER, mServerPort);
        intent.putExtra(TCPSocketService.CERT_ID, mCertId);
        return intent;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mParentView = view;

        // Toolbar setup
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setSupportActionBar(toolbar);
        //Navigation Drawer setup
        mDrawer = mParentView.findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                activity, mDrawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = mParentView.findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        //Setup swipe to refresh
        final SwipeRefreshLayout swipeRefreshLayout = mParentView.findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        new AsyncSocketRefresh(swipeRefreshLayout).executeOnExecutor(
                                AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                    }
                }
        );

        buttonSetup();
        mDataReceiver = new TCPBroadcastReceiver();
    }

    private void buttonSetup() {
        // Initialize Buttons
        FloatingActionButton refreshButton = mParentView.findViewById(R.id.refresh_fab);
        refreshOnClick(refreshButton);
        FloatingActionButton openButton = mParentView.findViewById(R.id.open_fab);
        writeMessageOnClick(openButton, TCPSocketService.GARAGE_OPEN);
        FloatingActionButton closeButton = mParentView.findViewById(R.id.close_fab);
        writeMessageOnClick(closeButton, TCPSocketService.GARAGE_CLOSE);
    }

    private void refreshOnClick(FloatingActionButton fab) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SwipeRefreshLayout swipeRefreshLayout =
                        mParentView.findViewById(R.id.swipe_refresh);
                if (mSocketBound) {
                    swipeRefreshLayout.setRefreshing(true);
                    new AsyncSocketRefresh(swipeRefreshLayout).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(getContext(), "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void writeMessageOnClick(FloatingActionButton fab, final String message) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    new AsyncSocketWriter(message).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(getContext(), "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onStop() {
        Log.d(TAG, "App stopped");
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mDataReceiver);
        mContext.unbindService(mConnection);
        super.onStop();
    }

    //TODO: Handle add server request
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        boolean serverSelected = false;
        switch (item.getItemId()) {
            case R.id.add_server_option:
                Toast.makeText(mContext, "Oops I didn't implement this yet.",
                        Toast.LENGTH_SHORT).show();
                serverSelected = false;
                break;
            case R.id.no_servers:
                return false; // Don't need to close the drawer if they tap this
        }

        mDrawer.closeDrawer(GravityCompat.START);
        return serverSelected;
    }

    /*
        Creates a service to bind to a TCPSocketService on which AsyncTasks are run
     */
    private class TCPServiceConnection implements ServiceConnection {

        // Called when the connection with the service is established
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Because we have bound to an explicit service that is running in our own process,
            // we can cast its IBinder to a concrete class and directly access it.
            TCPSocketService.SocketServiceBinder binder = (TCPSocketService.SocketServiceBinder) service;
            mSocketConnection = binder.getService();
            Log.i(TAG, "TCPSocketService has connected");
            mSocketBound = true;
        }

        // Called when the connection with the service disconnects unexpectedly
        @Override
        public void onServiceDisconnected(ComponentName className) {
            Log.i(TAG, "TCPSocketService has disconnected");
            mSocketBound = false;
        }
    }

    /*
        Should only have to be initialized once on creation.
        Receives status updates from TCPSocketServices.
     */
    private class TCPBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(TCPSocketService.DATA);
            // Don't run if there is no view or status text does not exist
            if (getView() == null || status == null)
                return;

            TextView statusView = getView().findViewById(R.id.door_status);
            if (status.equals(TCPSocketService.SERVERSIDE_DISCONNECT)) {
                //TODO: Server reconnect retry
                statusView.setText("CANNOT CONNECT TO SERVER");
            } else {
                // Data should always be received as a JSON String from the server
                try {
                    JSONObject json = new JSONObject(status);

                    if ((Boolean) json.get("OPEN"))
                        status = "OPEN";
                    else if ((Boolean) json.get("CLOSED"))
                        status = "CLOSED";
                    else if ((Boolean) json.get("CLOSING"))
                        status = "CLOSING";
                    else if ((Boolean) json.get("OPENING"))
                        status = "OPENING";
                    else
                        status = "NEITHER";
                } catch (JSONException e) {
                    Log.w(TAG, "Invalid JSON object: " + status);
                    e.printStackTrace();
                    status = "Invalid data received.";
                }
                statusView.setText(status);
            }
        }
    }

}
