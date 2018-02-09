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
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import computeythings.garagemonitor.TCPSocketService.SocketServiceBinder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MAIN_ACTIVITY";
    private SwipeRefreshLayout mRefreshLayout;
    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;
    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private boolean mSocketBound;

    private ServiceConnection mConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        mRefreshLayout = findViewById(R.id.swipe_refresh);
        mRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        new AsyncSocketRefresh(mRefreshLayout).executeOnExecutor(
                                AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                    }
                }
        );

        // Initialize Buttons
        FloatingActionButton refreshButton = findViewById(R.id.refresh_fab);
        refreshOnClick(refreshButton);
        FloatingActionButton openButton = findViewById(R.id.open_fab);
        writeMessageOnClick(openButton, TCPSocketService.GARAGE_OPEN);
        FloatingActionButton closeButton = findViewById(R.id.close_fab);
        writeMessageOnClick(closeButton, TCPSocketService.GARAGE_CLOSE);

        mDataReceiver = new TCPBroadcastReceiver();
    }

    //TODO: pull server settings from settings file
    /*
        Create the intent to start the server from the selected server in the settings
     */
    private Intent getServerFromSettings() {
        Intent intent = new Intent(this, TCPSocketService.class);
        intent.putExtra(TCPSocketService.SERVER_NAME, "");
        intent.putExtra(TCPSocketService.API_KEY, "");
        intent.putExtra(TCPSocketService.PORT_NUMBER, 4444);
        intent.putExtra(TCPSocketService.CERT_ID, R.raw.sslcrt);
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
                if (mSocketBound) {
                    mRefreshLayout.setRefreshing(true);
                    new AsyncSocketRefresh(mRefreshLayout).executeOnExecutor(
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
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
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
