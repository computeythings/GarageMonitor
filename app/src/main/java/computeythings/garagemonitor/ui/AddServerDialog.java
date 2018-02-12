package computeythings.garagemonitor.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import computeythings.garagemonitor.R;
import computeythings.garagemonitor.preferences.ServerPreferences;

/**
 * Dialog used to add and edit server settings. Upon adding a server, a callback is sent to the
 * host UI activity.
 * <p>
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
    public static final int READ_REQUEST_CODE = 444;

    private TextView mNameField;
    private TextView mAPIKeyField;
    private TextView mAddressField;
    private TextView mPortField;
    private TextView mCertField;
    private String mCertURI;

    public interface OnServerAddedListener {
        void onServerAdded(boolean isFirstServer);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final View dialogLayout = inflater.inflate(R.layout.dialog_addserver, null);
        builder.setView(dialogLayout);
        mNameField = dialogLayout.findViewById(R.id.server_name_value);
        mAddressField = dialogLayout.findViewById(R.id.server_address_value);
        mAPIKeyField = dialogLayout.findViewById(R.id.apikey_value);
        mPortField = dialogLayout.findViewById(R.id.port_value);
        mCertField = dialogLayout.findViewById(R.id.cert_value);

        // Upload cert file from local storage
        Button certSearchButton = dialogLayout.findViewById(R.id.cert_search_btn);
        certSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performFileSearch();
            }
        });

        if (savedInstanceState != null && savedInstanceState.containsKey(EDIT_KEY)) {
            mNameField.setText(savedInstanceState.getString(EDIT_NAME, ""));
            mAddressField.setText(savedInstanceState.getString(EDIT_ADDRESS, ""));
            mAPIKeyField.setText(savedInstanceState.getString(EDIT_API_KEY, ""));
            mPortField.setText(savedInstanceState.getInt(EDIT_PORT, 4444));
            mCertField.setText(savedInstanceState.getString(EDIT_CERT, ""));
        }

        builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                ServerPreferences prefs = new ServerPreferences(getContext());
                String serverName = mNameField.getText().toString().trim();
                String serverAddress = mAddressField.getText().toString().trim();
                String serverApiKey = mAPIKeyField.getText().toString().trim();
                int serverPort = Integer.parseInt(mPortField.getText().toString());

                if (serverName.equals(""))
                    serverName = serverAddress;

                // Store new server info in preferences
                prefs.addServer(serverName, serverAddress, serverApiKey, serverPort, mCertURI);

                // Send callback to host activity setting param to true if it is the first server
                // added to the application.
                if (prefs.getServerList().size() == 1) {
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

    /*
        Search file browser on local storage
     */
    private void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        // Filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Allow any file type as cert
        intent.setType("*/*");

        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    /*
        Responds to the activity result once a user has chosen a file
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();
                if (uri != null) {
                    mCertField.setText(getFilenameFromURI(uri));
                    mCertURI = uri.toString();
                }
            }
        }
    }

    /*
        Pulls the selected file name from the uri and sets it as the cert field value
     */
    private String getFilenameFromURI(Uri uri) {
        String filename = "Invalid File";
        Cursor returnCursor = getContext().getContentResolver().query(uri,
                null, null, null, null);

        if (returnCursor != null && returnCursor.moveToFirst()) {
            int nameIndex = returnCursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
            filename = returnCursor.getString(nameIndex);
            returnCursor.close();
        }

        return filename;
    }

}
