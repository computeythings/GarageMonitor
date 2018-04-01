package computeythings.garagemonitor.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.drawable.AnimationDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
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
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Set;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.async.AsyncSocketClose;
import computeythings.garagemonitor.async.AsyncSocketWriter;
import computeythings.garagemonitor.interfaces.SocketResultListener;
import computeythings.garagemonitor.preferences.ServerPreferences;
import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Main UI Fragment responsible for server setup and user interaction. Main body of code.
 * <p>
 * Created by bryan on 2/9/18.
 */

public class UIFragment extends Fragment
        implements NavigationView.OnNavigationItemSelectedListener, SocketResultListener {
    private static final String TAG = "UI_Fragment";
    private static final String STATE = "STATE";
    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_OPENING = "OPENING";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_CLOSING = "CLOSING";
    private static final String STATE_NONE = "NEITHER";
    private static final String STATE_DISCONNECTED = "DISCONNECTED";

    private String mSavedState;

    private Context mContext;
    private View mParentView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private DrawerLayout mDrawer;
    private Menu mServerMenu;
    private Menu mSettingsMenu;

    private BroadcastReceiver mDataReceiver;
    private TCPSocketService mSocketConnection;
    private ServiceConnection mConnection;
    private boolean mSocketBound;

    private ServerPreferences mPreferences;

    @Override
    public void onAttach(Context context) {
        mContext = context;
        super.onAttach(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // Enable fragment persist on configuration change
        setHasOptionsMenu(true); // Enable settings menu

        // Data receiver and preferences persist over multiple connections
        mDataReceiver = new TCPBroadcastReceiver();
        mPreferences = new ServerPreferences(mContext);
    }

    /*
        Create host view
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    /*
        UI setup once the parent view is initialized
     */
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mParentView = view; // This will be the parent view for the lifetime of this fragment

        //Navigation Drawer setup
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        if (mPreferences.getSelectedServer() != null)
            toolbar.setTitle(mPreferences.getSelectedServer());
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setSupportActionBar(toolbar);
        mDrawer = mParentView.findViewById(R.id.drawer_layout);
        // Add listener to toggle nav drawer from toolbar
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                activity, mDrawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navDrawer = mParentView.findViewById(R.id.nav_view);
        navDrawer.setNavigationItemSelectedListener(this);
        mServerMenu = navDrawer.getMenu().getItem(0).getSubMenu();

        // Populate menu
        updateServerList(false);

        //Setup swipe to refresh
        mSwipeRefreshLayout = mParentView.findViewById(R.id.swipe_refresh);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        if (mPreferences.getSelectedServer() != null) {
                            writeMessage(TCPSocketService.SEND_REFRESH);
                        } else {
                            mSwipeRefreshLayout.setRefreshing(false);
                        }

                    }
                }
        );

        buttonSetup();

        if (mPreferences.getServerList().size() == 0) {
            new AlertDialog.Builder(getContext())
                    .setTitle("No servers")
                    .setMessage("It looks like you have no servers setup. " +
                            "Would you like to set one up now?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            new AddServerDialog().show(getFragmentManager(), "new_server");
                        }
                    })
                    .setNegativeButton("No", null).show();
        }

        refreshDrawable();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Prepare to receive updates from this service
        LocalBroadcastManager.getInstance(mContext).registerReceiver((mDataReceiver),
                new IntentFilter(TCPSocketService.DATA_RECEIVED)
        );
        serverConnect();
    }

    private void refreshDrawable() {
        if (mSavedState != null) {
            ImageView statusView = mParentView.findViewById(R.id.door_status);
            switch (mSavedState) {
                case STATE_OPEN:
                    statusView.setImageResource(R.drawable.garage_open);
                    break;
                case STATE_CLOSED:
                    statusView.setImageResource(R.drawable.garage_closed);
                    break;
                case STATE_OPENING:
                    statusView.setImageResource(R.drawable.garage_opening_animation);
                    ((AnimationDrawable) statusView.getDrawable()).start();
                    break;
                case STATE_CLOSING:
                    statusView.setImageResource(R.drawable.garage_closing_animation);
                    ((AnimationDrawable) statusView.getDrawable()).start();
                    break;
                case STATE_NONE:
                    statusView.setImageResource(R.drawable.garage_middle);
                    break;
                default:
                    statusView.setImageResource(R.drawable.garage_disconnected);

            }
        }
    }

    /*
        Adds functionality to Open/Close/Refresh buttons
     */
    private void buttonSetup() {
        FloatingActionButton refreshButton = mParentView.findViewById(R.id.refresh_fab);
        writeMessageOnClick(refreshButton, TCPSocketService.SEND_REFRESH);
        FloatingActionButton openButton = mParentView.findViewById(R.id.open_fab);
        writeMessageOnClick(openButton, TCPSocketService.GARAGE_OPEN);
        FloatingActionButton closeButton = mParentView.findViewById(R.id.close_fab);
        writeMessageOnClick(closeButton, TCPSocketService.GARAGE_CLOSE);
    }

    /*
        Adds functionality to @param fab to write a custom message over the SSLSocket
     */
    private void writeMessageOnClick(FloatingActionButton fab, final String message) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mSocketBound) {
                    if (mPreferences.getSelectedServer() != null) {
                        writeMessage(message);
                    }
                } else {
                    Toast.makeText(getContext(), "No server connected.",
                            Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private void writeMessage(final String message) {
        mSwipeRefreshLayout.setRefreshing(true);

        if (mSocketConnection.isConnected()) {
            new AsyncSocketWriter(message, this).executeOnExecutor(
                    AsyncTask.SERIAL_EXECUTOR, mSocketConnection);
        } else {
            Toast.makeText(mContext, "Attempting to reconnect to server...",
                    Toast.LENGTH_SHORT).show();
            Handler sender = new Handler();
            sender.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mSocketConnection.socketOpen();
                    new AsyncSocketWriter(message, UIFragment.this)
                            .execute(mSocketConnection);
                }
            }, 3000);
        }
    }

    /*
        Unbind service and broadcast receiver when the fragment is no longer active
     */
    @Override
    public void onDetach() {
        super.onDetach();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mDataReceiver);
        mContext.unbindService(mConnection);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        String selected = item.getTitle().toString();
        // Add server functionality
        if (selected.equals(getResources().getString(R.string.add_server))) {
            new AddServerDialog().show(getFragmentManager(), "new_server");
            // Any other option will be a server unless it is the empty server placeholder item
        } else if (!selected.equals(getResources().getString(R.string.empty_server_menu))) {
            String currentServer = mPreferences.getSelectedServer();

            // Connect to the selected server
            if (currentServer == null) {
                mPreferences.setSelectedServer(selected);
                // start new service and connect
                serverConnect();
                // Kill any existing server connections if they are available
            } else if (!currentServer.equals(selected)) {
                mSavedState = STATE_DISCONNECTED;
                // Kill the running service
                mContext.unbindService(mConnection);
                mSocketBound = false;

                mPreferences.setSelectedServer(selected);
                updateServerList(false);
                // start new service and connect
                serverConnect();
            }
            // Don't close the drawer if an invalid option was selected
        } else {
            return false; // Touch was not consumed
        }

        // Close drawer
        mDrawer.closeDrawer(GravityCompat.START);
        return true; // Touch was consumed
    }

    public DrawerLayout getDrawerLayout() {
        return mDrawer;
    }

    /*
        Updates the server list to the most current state
     */
    public void updateServerList(boolean isFirstServer) {
        mServerMenu.clear(); // Complete reset

        // Add all the servers in saved server list
        Set<String> serverList = mPreferences.getServerList();
        if (serverList.size() > 0) {
            for (String server : serverList) {
                mServerMenu.add(server).setCheckable(true).setChecked(
                        server.equals(mPreferences.getSelectedServer()));
            }
            if (isFirstServer) {
                mContext.startService(mPreferences.getStartIntent(
                        mPreferences.getSelectedServer()));
                serverConnect();
            }
        } else
            mServerMenu.add(R.string.empty_server_menu); // Placeholder if there are no servers
        if (mSettingsMenu != null)
            this.onPrepareOptionsMenu(mSettingsMenu);
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        toolbar.setTitle(mPreferences.getSelectedServer());
    }

    /*
        Connects to the last server that was connected by binding to the running TCPSocketService
     */
    private void serverConnect() {
        if (mPreferences.getSelectedServer() == null)
            return; // Quit if there is no valid server to connect to

        // Create and bind a socket service based on currently selected server
        mConnection = new TCPServiceConnection();
        mContext.bindService(mPreferences.getStartIntent(mPreferences.getSelectedServer()),
                mConnection, Context.BIND_AUTO_CREATE);
    }

    public void serverDeleted() {
        // Remove all traces of current server and its connection
        mContext.unbindService(mConnection);
        mConnection = null;
        mSocketConnection = null;
        mPreferences.removeServer(mPreferences.getSelectedServer());
        new AsyncSocketClose().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.app_name);

        // Update the server list
        updateServerList(false);
    }

    /*
        Triple dot menu creation
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.settings, menu);
        mSettingsMenu = menu;
    }

    /*
        Run ever time the triple dot menu is made visible
     */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // Hide server specific options if there is no selected server.
        menu.findItem(R.id.action_edit_server).setVisible(mPreferences.getSelectedServer() != null);
    }

    /*
        Executed on triple dot menu item selection
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_edit_server) {
            try {
                JSONObject serverInfo = new JSONObject(
                        mPreferences.getServerInfo(mPreferences.getSelectedServer()));

                DialogFragment dialog = new AddServerDialog();
                Bundle editInfo = new Bundle();

                editInfo.putString(AddServerDialog.EDIT_NAME,
                        serverInfo.getString(ServerPreferences.SERVER_NAME));
                editInfo.putString(AddServerDialog.EDIT_ADDRESS,
                        serverInfo.getString(ServerPreferences.SERVER_ADDRESS));
                editInfo.putString(AddServerDialog.EDIT_API_KEY,
                        serverInfo.getString(ServerPreferences.SERVER_API_KEY));
                editInfo.putString(AddServerDialog.EDIT_PORT,
                        serverInfo.getString(ServerPreferences.SERVER_PORT));
                editInfo.putString(AddServerDialog.EDIT_CERT,
                        serverInfo.getString(ServerPreferences.SERVER_CERT));

                dialog.setArguments(editInfo);
                dialog.show(getFragmentManager(), "new_server");
            } catch (JSONException e) {
                Log.e(TAG, "Invalid server info received");
                e.printStackTrace();
                return false;
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSocketResult(Boolean success) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (!success) {
            Toast.makeText(mContext, "Could not reach server", Toast.LENGTH_SHORT).show();
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
                mContext.bindService(mPreferences.getStartIntent(
                        mPreferences.getSelectedServer()), this,
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
            if (mParentView == null || status == null)
                return;

            if (intent.getAction() != null &&
                    intent.getAction().equals(TCPSocketService.DATA_RECEIVED)) {
                if (status.equals(TCPSocketService.SERVER_SIDE_DISCONNECT)) {
                    Log.d(TAG, "Received server-side disconnect");
                    mSavedState = STATE_DISCONNECTED;
                    if (mSocketConnection != null)
                        mSocketConnection.socketClose();
                } else {
                    // Data should always be received as a JSON String from the server
                    try {
                        JSONObject json = new JSONObject(status);
                        switch((String) json.get(STATE)) {
                            case STATE_OPEN:
                                mSavedState = STATE_OPEN;
                                break;
                            case STATE_OPENING:
                                mSavedState = STATE_OPENING;
                                break;
                            case STATE_CLOSED:
                                mSavedState = STATE_CLOSED;
                                break;
                            case STATE_CLOSING:
                                mSavedState = STATE_CLOSING;
                                break;
                            default:
                                mSavedState = STATE_NONE;
                        }
                    } catch (JSONException e) {
                        Log.w(TAG, "Invalid JSON object: " + status);
                        e.printStackTrace();
                        mSavedState = STATE_NONE;
                    }
                }
            }
            refreshDrawable();
        }
    }
}
