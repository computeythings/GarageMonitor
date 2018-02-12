package computeythings.garagemonitor.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.preferences.ServerPreferences;

/**
 * Dialog used to add and edit server settings. Upon adding a server, a callback is sent to the
 * host UI activity.
 *
 * Created by bryan on 2/10/18.
 */

public class AddServerDialog extends DialogFragment {
    private static final String TAG = "ADD_SERVER_DIALOG";
    public static final String EDIT_KEY = "IS_EDIT";
    public static final String EDIT_NAME = "EDIT_NAME";
    public static final String EDIT_ADDRESS = "EDIT_ADDRESS";
    public static final String EDIT_API_KEY = "EDIT_API_KEY";
    public static final String EDIT_PORT = "EDIT_PORT";
    public static final String EDIT_CERT = "EDIT_CERT";

    public interface OnServerAddedListener {
        public void onServerAdded(boolean isFirstServer);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final View dialogLayout = inflater.inflate(R.layout.dialog_addserver, null);
        builder.setView(dialogLayout);
        final TextView nameField = dialogLayout.findViewById(R.id.server_name_value);
        final TextView addressField = dialogLayout.findViewById(R.id.server_address_value);
        final TextView apiKeyField = dialogLayout.findViewById(R.id.apikey_value);
        final TextView portField = dialogLayout.findViewById(R.id.port_value);

        if (savedInstanceState != null && savedInstanceState.containsKey(EDIT_KEY)) {
            nameField.setText(savedInstanceState.getString(EDIT_NAME, ""));
            addressField.setText(savedInstanceState.getString(EDIT_ADDRESS, ""));
            apiKeyField.setText(savedInstanceState.getString(EDIT_API_KEY, ""));
            portField.setText(savedInstanceState.getInt(EDIT_PORT, 4444));
        }

        builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ServerPreferences prefs = new ServerPreferences(getContext());
                String serverName = nameField.getText().toString().trim();
                String serverAddress = addressField.getText().toString().trim();
                String serverApiKey = apiKeyField.getText().toString().trim();
                int serverPort = Integer.parseInt(portField.getText().toString());
                int serverCert = R.raw.sslcrt; //TODO: file search for cert location

                if(serverName.equals(""))
                    serverName = serverAddress;

                // Store new server info in preferences
                prefs.addServer(serverName, serverAddress, serverApiKey, serverPort, serverCert);

                // Send callback to host activity setting param to true if it is the first server
                // added to the application.
                if(prefs.getServerList().size() == 1) {
                    prefs.setSelectedServer(serverName);
                    ((OnServerAddedListener) getHost()).onServerAdded(true);
                } else
                    ((OnServerAddedListener) getHost()).onServerAdded(false);
            }
        })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        AddServerDialog.this.getDialog().cancel();
                    }
                })
                .setTitle("Add a Server");
        return builder.create();
    }
}
