package computeythings.piopener.ui;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
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

import java.util.HashMap;
import java.util.Set;

import computeythings.piopener.R;
import computeythings.piopener.async.SocketConnector;
import computeythings.piopener.interfaces.SocketResultListener;
import computeythings.piopener.preferences.ServerPreferences;
import computeythings.piopener.services.FCMService;

/**
 * Main UI Fragment responsible for server setup and user interaction. Main body of code.
 * <p>
 * Created by bryan on 2/9/18.
 */

public class UIFragment extends Fragment
        implements NavigationView.OnNavigationItemSelectedListener, SocketResultListener {
    private static final String TAG = "UI_Fragment";
    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_OPENING = "OPENING";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_CLOSING = "CLOSING";
    private static final String STATE_NONE = "NONE";
    private static final String STATE_DISCONNECTED = "DISCONNECTED";

    private String mSavedState;

    private Context mContext;
    private View mParentView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private DrawerLayout mDrawer;
    private Menu mServerMenu;
    private Menu mSettingsMenu;

    private SocketConnector mServer;
    private ServerPreferences mPreferences;
    private BroadcastReceiver mBroadcastReceiver;

    // Socket interactions //
    /*
        Creates a new SocketConnector from the currently selected server information
     */
    private void socketConnect() {
        if (mPreferences.getSelectedServer() == null)
            return; // quit if there is no valid server to connect to


        if (mServer == null || mServer.isDisconnected()) {
            String currentServer = mPreferences.getSelectedServer();
            // create and bind a socket based on currently selected server
            mServer = SocketConnector.fromInfo(mPreferences.getServerInfo(currentServer), mContext,
                    this);
        }
    }

    public boolean socketWrite(String message) {
        mSwipeRefreshLayout.setRefreshing(true);
        if (mServer != null) {
            mServer.socketWrite(message);
            return true;
        }
        Log.e(TAG, mPreferences.getServerList().size() + "");
        return false;
    }

    /*
        Tell specified SocketConnector to close connection.
     */
    public void socketClose() {
        if (mServer != null)
            mServer.socketClose();
        mServer = null;
        mSavedState = STATE_DISCONNECTED;
    }

    /*
        Callback function for when transactions are sent over socket connections
     */
    @Override
    public void onSocketResult(boolean success) {
        mSwipeRefreshLayout.setRefreshing(false);
        if (!success) {
            Toast.makeText(mContext, "Could not connect to server", Toast.LENGTH_SHORT).show();
            mSavedState = STATE_DISCONNECTED;
        } else {
            mSavedState = mPreferences.getServerInfo(mPreferences.getSelectedServer())
                    .get(ServerPreferences.LAST_STATE);
        }
        refreshDrawable();
    }

    /*
        Refresh UI when receiving data over SSL socket
     */
    @Override
    public void onSocketData(String data) {
        mSwipeRefreshLayout.setRefreshing(false);
        if(data.equals(STATE_CLOSED) || data.equals(STATE_CLOSING) || data.equals(STATE_OPEN) ||
                data.equals(STATE_OPENING) || data.equals(STATE_NONE)) {
            mSavedState = data;
            refreshDrawable();
        }
    }

    /*
        Callback for when servers are deleted via server edit menu
     */
    public void serverDeleted(String server) {
        if (server.equals(mPreferences.getSelectedServer())) {
            socketClose();
            refreshDrawable();
        }

        mPreferences.removeServer(mPreferences.getSelectedServer());
        // update the server list
        updateServerList();
    }

    public void serverModified(String server) {
        if (mPreferences.getSelectedServer() == null) {
            mPreferences.setSelectedServer(server);
            socketConnect();
        } else if (mPreferences.getSelectedServer().equals(server))
            socketConnect();
        updateServerList();
    }

    // UI setup and control //
    /*
        Updates the server list to the most current state
     */
    private void updateServerList() {
        mServerMenu.clear(); // Complete reset

        // add all the servers in saved server list
        Set<String> serverList = mPreferences.getServerList();
        if (serverList.size() > 0) {
            for (String server : serverList) {
                mServerMenu.add(server).setCheckable(true).setChecked(
                        server.equals(mPreferences.getSelectedServer()));
            }
        } else
            mServerMenu.add(R.string.empty_server_menu); // placeholder if there are no servers
        if (mSettingsMenu != null) {
            this.onPrepareOptionsMenu(mSettingsMenu);
        }

        // Update toolbar title to reflect any newly selected/deselected server
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        if (mPreferences.getSelectedServer() != null) {
            toolbar.setTitle(mPreferences.getSelectedServer());
        } else {
            toolbar.setTitle(R.string.app_name);
        }
    }

    /*
        Adds functionality to Open/Close/Refresh buttons
     */
    private void buttonSetup() {
        FloatingActionButton refreshButton = mParentView.findViewById(R.id.refresh_fab);
        refreshOnClick(refreshButton);
        FloatingActionButton openButton = mParentView.findViewById(R.id.open_fab);
        writeMessageOnClick(openButton, SocketConnector.GARAGE_OPEN);
        FloatingActionButton closeButton = mParentView.findViewById(R.id.close_fab);
        writeMessageOnClick(closeButton, SocketConnector.GARAGE_CLOSE);
    }

    private void refreshOnClick(FloatingActionButton fab) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mSwipeRefreshLayout.setRefreshing(true);
                refreshState();
            }
        });
    }

    /*
        Adds functionality to @param fab to write a custom message over the SSLSocket
     */
    private void writeMessageOnClick(FloatingActionButton fab, final String message) {
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mServer != null && !mServer.isDisconnected()) {
                    if (mPreferences.getSelectedServer() == null || !socketWrite(message)) {
                        Toast.makeText(getContext(), "Could not connect to send message.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }

    private void refreshState() {
        if (mServer != null && !mServer.isDisconnected()) {
            if (mPreferences.getSelectedServer() == null ||
                    !socketWrite(SocketConnector.SEND_REFRESH)) {
                Toast.makeText(getContext(), "Could not connect to send message.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /*
        Updates app background image
     */
    private void refreshDrawable() {
        if (mSavedState != null) {
            ImageView statusView = mParentView.findViewById(R.id.door_status);
            if (statusView == null)
                return; // don't try to set drawable if the view doesn't exist

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
        } else {
            if (mPreferences.getSelectedServer() != null) {
                // Show a loading wheel on boot (when mSavedState is null) until connected
                mSwipeRefreshLayout.setRefreshing(true);
            }
        }
    }

    /*
        Triple dot menu creation
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.settings, menu);
        mSettingsMenu = menu;
    }

    /*
        Run every time the triple dot menu is made visible
     */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        // hide server specific options if there is no selected server.
        menu.findItem(R.id.action_edit_server).setVisible(mPreferences.getSelectedServer() != null);
    }

    /*
        Executed on triple dot menu item selection
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_edit_server) {
            HashMap<String, String> serverInfo = mPreferences.getServerInfo(
                    mPreferences.getSelectedServer());
            DialogFragment dialog = new AddServerDialog();
            Bundle editInfo = new Bundle();
            /*editInfo.putString(AddServerDialog.EDIT_NAME,
                    serverInfo.get(ServerPreferences.SERVER_NAME));
            editInfo.putString(AddServerDialog.EDIT_ADDRESS,
                    serverInfo.get(ServerPreferences.SERVER_ADDRESS));
            editInfo.putString(AddServerDialog.EDIT_API_KEY,
                    serverInfo.get(ServerPreferences.SERVER_API_KEY));
            editInfo.putString(AddServerDialog.EDIT_PORT,
                    serverInfo.get(ServerPreferences.SERVER_PORT));
            editInfo.putString(AddServerDialog.EDIT_CERT,
                    serverInfo.get(ServerPreferences.SERVER_CERT));*/
            editInfo.putSerializable(AddServerDialog.EDIT_INFO, serverInfo);
            dialog.setArguments(editInfo);
            dialog.show(getFragmentManager(), "new_server");
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public DrawerLayout getDrawerLayout() {
        return mDrawer;
    }

    /*
        Executed on server menu item select
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        String selected = item.getTitle().toString();
        // add server functionality
        if (selected.equals(getResources().getString(R.string.add_server))) {
            new AddServerDialog().show(getFragmentManager(), "new_server");
            // any other option will be a server unless it is the empty server placeholder item
        } else if (!selected.equals(getResources().getString(R.string.empty_server_menu))) {
            String currentServer = mPreferences.getSelectedServer();

            // connect to the selected server
            if (currentServer == null) {
                mPreferences.setSelectedServer(selected);
                updateServerList();
                // start new socket connection
                socketConnect();
                // kill any existing server connections if they are available
            } else if (!currentServer.equals(selected)) {
                socketClose();
                mPreferences.setSelectedServer(selected);
                updateServerList();
                // start new socket connection
                socketConnect();
            }
            // don't close the drawer if an invalid option was selected
        } else {
            return false; // touch was not consumed
        }

        // close drawer
        mDrawer.closeDrawer(GravityCompat.START);
        return true; // Touch was consumed
    }

    // Application Lifecycle //
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true); // enable fragment persist on configuration change
        setHasOptionsMenu(true); // enable settings menu

        // data receiver and preferences persist over multiple connections
        mContext = getContext();
        mPreferences = new ServerPreferences(mContext);
        mBroadcastReceiver = new MessageReceiver();
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mBroadcastReceiver,
                new IntentFilter(FCMService.SERVER_UPDATE_RECEIVED));
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
        mParentView = view; // this will be the parent view for the lifetime of this fragment

        // navigation drawer setup
        Toolbar toolbar = mParentView.findViewById(R.id.toolbar);
        if (mPreferences.getSelectedServer() != null)
            toolbar.setTitle(mPreferences.getSelectedServer());
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setSupportActionBar(toolbar);
        mDrawer = mParentView.findViewById(R.id.drawer_layout);
        // add listener to toggle nav drawer from toolbar
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                activity, mDrawer, toolbar, R.string.navigation_drawer_open,
                R.string.navigation_drawer_close);
        mDrawer.addDrawerListener(toggle);
        toggle.syncState();
        NavigationView navDrawer = mParentView.findViewById(R.id.nav_view);
        navDrawer.setNavigationItemSelectedListener(this);
        mServerMenu = navDrawer.getMenu().getItem(0).getSubMenu();

        // populate menu
        updateServerList();
        // setup swipe to refresh
        mSwipeRefreshLayout = mParentView.findViewById(R.id.swipe_refresh);
        mSwipeRefreshLayout.setOnRefreshListener(
                new SwipeRefreshLayout.OnRefreshListener() {
                    @Override
                    public void onRefresh() {
                        refreshState();
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
        socketConnect();
    }

    @Override
    public void onPause() {
        if(!getActivity().isChangingConfigurations())
            socketClose();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        socketClose();
        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mBroadcastReceiver);
        super.onDestroy();
    }

    /*
        Broadcast receiver to accept data sent via FCM
     */
    private class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSwipeRefreshLayout.setRefreshing(false);
            if (getCurrentRefId() != null &&
                    getCurrentRefId().equals(intent.getStringExtra(ServerPreferences.SERVER_REFID)))
            {
                mSavedState = mPreferences.getServerInfo(mPreferences.getSelectedServer())
                        .get(ServerPreferences.LAST_STATE);
            }
            refreshDrawable();
        }
    }

    private String getCurrentRefId() {
        if(mPreferences.getSelectedServer() == null)
            return null;
        return mPreferences.getServerInfo(mPreferences.getSelectedServer())
                .get(ServerPreferences.SERVER_REFID);
    }
}
