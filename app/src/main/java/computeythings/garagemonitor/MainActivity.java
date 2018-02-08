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
    private static final String TAG = "MAINACTIVITY";
    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private boolean mSocketBound;

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            // Because we have bound to an explicit
            // service that is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            SocketServiceBinder binder = (SocketServiceBinder) service;
            mSocketConnection = binder.getService();
            mSocketBound = true;
        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.e(TAG, "TCPSocketService has disconnected");
            mSocketConnection.socketClose();
            mSocketConnection = null;
            mSocketBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        FloatingActionButton refreshButton = findViewById(R.id.refresh_fab);
        FloatingActionButton openButton = findViewById(R.id.open_fab);
        FloatingActionButton closeButton = findViewById(R.id.close_fab);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    new AsyncSocketRefresh().executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(MainActivity.this, "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        openButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    new AsyncSocketWriter(TCPSocketService.GARAGE_OPEN).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(MainActivity.this, "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    new AsyncSocketWriter(TCPSocketService.GARAGE_CLOSE).executeOnExecutor(
                            AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(MainActivity.this, "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        mDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction() == null)
                    return;

                String status = intent.getStringExtra(TCPSocketService.DATA);
                if (status.equals(TCPSocketService.SERVERSIDE_DISCONNECT)) {
                    //TODO: Server reconnect retry
                    ((TextView) findViewById(R.id.door_status)).setText("CANNOT CONNECT TO SERVER");
                } else {
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
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, TCPSocketService.class);
        intent.putExtra(TCPSocketService.SERVER_NAME, "server");//TODO: pull server settings from settings file
        intent.putExtra(TCPSocketService.API_KEY, "<APIKEY>");
        intent.putExtra(TCPSocketService.PORT_NUMBER, 4444);
        intent.putExtra(TCPSocketService.CERT_ID, R.raw.sslcrt);

        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        LocalBroadcastManager.getInstance(this).registerReceiver((mDataReceiver),
                new IntentFilter(TCPSocketService.DATA_RECEIVED)
        );
    }

    @Override
    protected void onStop() {
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
}
