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
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import computeythings.garagemonitor.TCPSocketService.SocketServiceBinder;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {
    private static final String TAG = "MAIN_ACTIVITY";
    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private DrawerLayout mDrawer;
    private boolean mSocketBound;
    private String mServerName;
    private String mAPIKey;
    private int mCertId;
    private int mServerPort = 4444;

    private ServiceConnection mConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //Navigation Drawer setup
        mDrawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        //Setup swipe to refresh
        final SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh);
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
        FloatingActionButton refreshButton = findViewById(R.id.refresh_fab);
        refreshOnClick(refreshButton);
        FloatingActionButton openButton = findViewById(R.id.open_fab);
        writeMessageOnClick(openButton, TCPSocketService.GARAGE_OPEN);
        FloatingActionButton closeButton = findViewById(R.id.close_fab);
        writeMessageOnClick(closeButton, TCPSocketService.GARAGE_CLOSE);
    }

    /*
        Create the intent to start the server from the selected server in the settings
     */
    private Intent getServerFromSettings() {
        Intent intent = new Intent(this, TCPSocketService.class);
        intent.putExtra(TCPSocketService.SERVER_NAME, mServerName);
        intent.putExtra(TCPSocketService.API_KEY, mAPIKey);
        intent.putExtra(TCPSocketService.PORT_NUMBER, mServerPort);
        intent.putExtra(TCPSocketService.CERT_ID, mCertId);
        return intent;
    }

    private void writeMessageOnClick(FloatingActionButton fab, final String message) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    new AsyncSocketWriter(message).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(MainActivity.this, "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void refreshOnClick(FloatingActionButton fab) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SwipeRefreshLayout swipeRefreshLayout = findViewById(R.id.swipe_refresh);
                if (mSocketBound) {
                    swipeRefreshLayout.setRefreshing(true);
                    new AsyncSocketRefresh(swipeRefreshLayout).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(MainActivity.this, "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        //TODO: pull server settings from settings file
        mServerName = "";
        mAPIKey = "";
        mCertId = R.raw.sslcrt;
        mServerPort = 4444;

        mConnection = new TCPServiceConnection();
        // Create and bind a socket service based on currently selected server
        bindService(getServerFromSettings(), mConnection, Context.BIND_AUTO_CREATE);
        // Prepare to receive updates from this service
        LocalBroadcastManager.getInstance(this).registerReceiver((mDataReceiver),
                new IntentFilter(TCPSocketService.DATA_RECEIVED)
        );
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "App stopped");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mDataReceiver);
        unbindService(mConnection);
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (mDrawer.isDrawerOpen(GravityCompat.START)) {
            mDrawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    //TODO: Handle add server request
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        boolean serverSelected = false;
        switch(item.getItemId()) {
            case R.id.add_server_option:
                Toast.makeText(this, "Oops I didn't implement this yet.",
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
        Should only have to be initialized once on creation.
        Receives status updates from TCPSocketServices.
     */
    private class TCPBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(TCPSocketService.DATA);
            if (status == null) // We'll be treating the status as a String value
                return;

            if (status.equals(TCPSocketService.SERVERSIDE_DISCONNECT)) {
                //TODO: Server reconnect retry
                ((TextView) findViewById(R.id.door_status)).setText("CANNOT CONNECT TO SERVER");
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
                ((TextView) findViewById(R.id.door_status)).setText(status);
            }
        }
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
            SocketServiceBinder binder = (SocketServiceBinder) service;
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
}
