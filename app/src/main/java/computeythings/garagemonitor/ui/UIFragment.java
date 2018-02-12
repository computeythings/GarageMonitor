package computeythings.garagemonitor.ui;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.v4.app.DialogFragment;
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
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.ServiceConfigurationError;
import java.util.Set;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.async.AsyncSocketRefresh;
import computeythings.garagemonitor.async.AsyncSocketWriter;
import computeythings.garagemonitor.preferences.ServerPreferences;
import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Created by bryan on 2/9/18.
 */

public class UIFragment extends Fragment
        implements NavigationView.OnNavigationItemSelectedListener, View.OnLongClickListener {
    private static final String TAG = "UI_Fragment";
    private Context mContext;
    private View mParentView;
    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private boolean mSocketBound;
    private ServiceConnection mConnection;

    protected DrawerLayout mDrawer;
    protected ServerPreferences mPreferences;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mContext = context;
        mPreferences = new ServerPreferences(mContext);

        if(mPreferences.getSelectedServer() != null)
            mContext.startService(getServerFromSettings());
    }

    @Override
    public void onResume() {
        super.onResume();

        serverConnect();
    }

    private void serverConnect() {
        if(mPreferences.getSelectedServer() == null)
            return;
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
        //TODO: pull server settings from settings file
        try {
            JSONObject server = new JSONObject(mPreferences.getServerInfo(
                    mPreferences.getSelectedServer()));
            intent.putExtra(TCPSocketService.SERVER_ADDRESS, server.getString(
                    ServerPreferences.SERVER_ADDRESS));
            intent.putExtra(TCPSocketService.API_KEY, server.getString(
                    ServerPreferences.SERVER_API_KEY));
            intent.putExtra(TCPSocketService.PORT_NUMBER, server.getInt(
                    ServerPreferences.SERVER_PORT));
            intent.putExtra(TCPSocketService.CERT_ID, server.getInt(
                    ServerPreferences.SERVER_CERT));
            return intent;
        } catch (JSONException e) {
            Log.e(TAG, "Invalid server settings");
            e.printStackTrace();
        }
        return null;
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
        navigationView.setOnLongClickListener(this);

        // Populate menu
        updateServerList();

        //Setup swipe to refresh
        final SwipeRefreshLayout swipeRefreshLayout = mParentView.findViewById(R.id.swipe_refresh);
        swipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        new AsyncSocketRefresh(swipeRefreshLayout).executeOnExecutor(
                                AsyncTask.SERIAL_EXECUTOR, mSocketConnection);
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
                            AsyncTask.SERIAL_EXECUTOR, mSocketConnection);
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
                            AsyncTask.SERIAL_EXECUTOR, mSocketConnection);
                } else {
                    Toast.makeText(getContext(), "Server disconnected!",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    @Override
    public void onStop() {
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mDataReceiver);
        mContext.unbindService(mConnection);
        super.onStop();
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        // todo: move to strings resource file
        String selected = item.getTitle().toString();
        if(selected.equals("Add Server")) {
            DialogFragment dialog = new AddServerDialog();
            dialog.show(getFragmentManager(), "new_server");
        } else if(!selected.equals("No servers")) {
            String currentServer = mPreferences.getSelectedServer();

            if(currentServer == null) {
                mPreferences.setSelectedServer(selected);
                // start new service and connect
                mContext.startService(getServerFromSettings());
                serverConnect();
            } else if(!currentServer.equals(selected)) {
                // Kill the running service connect to a different server
                mContext.unbindService(mConnection);
                mContext.stopService(getServerFromSettings());

                mPreferences.setSelectedServer(selected);
                // start new service and connect
                mContext.startService(getServerFromSettings());
                serverConnect();
            }
        } else {
            return false;
        }

        mDrawer.closeDrawer(GravityCompat.START);
        return true;
    }

    @Override
    public boolean onLongClick(View view) {
        Log.d("DEBUG", "LONG PRESS REGISTERED");
        if(view instanceof MenuItem) {
            String selected = ((MenuItem) view).getTitle().toString();
            if(mPreferences.getServerList().contains(selected)) {
                try {
                    JSONObject serverInfo = new JSONObject(mPreferences.getServerInfo(selected));

                    DialogFragment dialog = new AddServerDialog();
                    Bundle editInfo = new Bundle();

                    editInfo.keySet().add(AddServerDialog.EDIT_KEY);
                    editInfo.putString(AddServerDialog.EDIT_NAME,
                            serverInfo.getString(ServerPreferences.SERVER_NAME));
                    editInfo.putString(AddServerDialog.EDIT_ADDRESS,
                            serverInfo.getString(ServerPreferences.SERVER_ADDRESS));
                    editInfo.putString(AddServerDialog.EDIT_API_KEY,
                            serverInfo.getString(ServerPreferences.SERVER_API_KEY));
                    editInfo.putInt(AddServerDialog.EDIT_PORT,
                            serverInfo.getInt(ServerPreferences.SERVER_PORT));

                    dialog.setArguments(editInfo);
                    dialog.show(getFragmentManager(), "new_server");
                } catch (JSONException e) {
                    Log.e(TAG, "Invalid server info received");
                    e.printStackTrace();
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /*
        Updates the server list to the most current state
     */
    public void updateServerList() {
        NavigationView nav = mParentView.findViewById(R.id.nav_view);
        Menu menu = nav.getMenu().getItem(0).getSubMenu();
        Set<String> serverList = mPreferences.getServerList();
        if (serverList.size() > 0) {
            menu.clear();
            for (String server : serverList) {
                if(server.equals(mPreferences.getSelectedServer()))
                    menu.add(server).setCheckable(true).setChecked(true); // Check selected server
                else
                    menu.add(server).setCheckable(true);
            }
            if(mSocketConnection == null) {
                mContext.startService(getServerFromSettings());
                serverConnect();
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
            TCPSocketService.SocketServiceBinder binder =
                    (TCPSocketService.SocketServiceBinder) service;
            // If the binder is null then that means the socket connection no longer exists;
            // in that case we kill the currently running service create the new desired socket
            if (binder == null) {
                mContext.stopService(new Intent(mContext, TCPSocketService.class));
                mContext.startService(getServerFromSettings());
                mContext.bindService(getServerFromSettings(), this,
                        Context.BIND_AUTO_CREATE);
                return;
            }
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
