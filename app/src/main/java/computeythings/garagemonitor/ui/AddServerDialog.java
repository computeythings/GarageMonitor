package computeythings.garagemonitor.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
    public static final String EDIT_NAME = "EDIT_NAME";
    public static final String EDIT_ADDRESS = "EDIT_ADDRESS";
    public static final String EDIT_API_KEY = "EDIT_API_KEY";
    public static final String EDIT_PORT = "EDIT_PORT";
    public static final String EDIT_CERT = "EDIT_CERT";
    private static final int READ_REQUEST_CODE = 444;

    private ServerPreferences mPrefs;
    private String mSelectedField;
    private TextView mNameField;
    private TextView mAPIKeyField;
    private TextView mAddressField;
    private TextView mPortField;
    private TextView mCertField;
    private TextView mClearCertButton;
    private String mCertURI;
    private boolean isEdit = false;

    public interface OnServerListChangeListener {
        void onServerAdded(boolean isFirstServer);

        void onServerDeleted();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        Bundle args = getArguments();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final View dialogLayout = inflater.inflate(R.layout.dialog_addserver, null);
        builder.setView(dialogLayout);

        mClearCertButton = dialogLayout.findViewById(R.id.clear_text_button);
        mClearCertButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mCertField.setText("");
                mCertURI = null;
                mClearCertButton.setVisibility(View.GONE);
            }
        });

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

        if (args != null) { // args are null on add and initialized on edit
            mNameField.setText(args.getString(EDIT_NAME, ""));

            // Address field shouldn't need to be changed so we'll disallow it
            mAddressField.setText(args.getString(EDIT_ADDRESS, ""));
            mAddressField.setFocusable(false);
            mAddressField.setBackground(null);

            mAPIKeyField.setText(args.getString(EDIT_API_KEY, ""));
            mPortField.setText(args.getString(EDIT_PORT, ""));

            String location = args.getString(EDIT_CERT, "");
            if (location.equals(""))
                mCertField.setHint("N/A");
            else {
                String certLocation = location.substring(location.lastIndexOf(File.separator) + 1);
                mCertField.setText(certLocation);
                mClearCertButton.setVisibility(View.VISIBLE);
            }

            mCertURI = args.getString(EDIT_CERT);

            builder.setNeutralButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    confirmDelete((OnServerListChangeListener) getHost());
                }
            });
            builder.setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                        /* No implementation as it is overridden in onResume() */
                }
            });

            isEdit = true;
        } else {
            builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                        /* No implementation as it is overridden in onResume() */
                }
            });
        }

        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                AddServerDialog.this.getDialog().cancel();
            }
        })
                .setTitle("Add a Server");
        return builder.create();
    }

    @Override
    public void onResume() {
        super.onResume();

        // We have to define the onClick method for the positive button here in order to overwrite
        // the default behavior to dismiss the dialog regardless of form validity.
        AlertDialog self = (AlertDialog) getDialog();
        if (self != null) {
            Button positiveButton = self.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mPrefs = new ServerPreferences(getContext());
                    String serverName = mNameField.getText().toString().trim();
                    String serverAddress = mAddressField.getText().toString().trim();
                    String serverApiKey = mAPIKeyField.getText().toString().trim();
                    String serverPort = mPortField.getText().toString().trim();
                    String certLocation = mCertField.getText().toString().trim();

                    if (serverName.equals(""))
                        serverName = serverAddress;
                    if (serverPort.equals(""))
                        serverPort = "4444";
                    if (certLocation.equals("") && mCertURI == null)
                        mCertURI = certLocation;

                    if (serverAddress.length() <= 0 || serverApiKey.length() <= 0) {
                        if (serverApiKey.length() <= 0)
                            mAPIKeyField.setError("This server requires an API key.");
                        if (serverAddress.length() <= 0)
                            mAddressField.setError("You must add a server.");
                        return;
                    }

                    try {
                        // Attempt to save any uploaded cert locally
                        saveCertToServer(serverName);
                    } catch (IOException e) {
                        mCertField.setError("Error accessing certificate");
                        e.printStackTrace();
                        return;
                    }
                    // Store new server info in preferences
                    mPrefs.addServer(serverName, serverAddress, serverApiKey,
                            Integer.parseInt(serverPort), mCertURI);

                    // Send callback to host activity setting param to true if it is the first server
                    // added to the application.
                    if (mPrefs.getServerList().size() == 1) {
                        mPrefs.setSelectedServer(serverName);
                        ((OnServerListChangeListener) getHost()).onServerAdded(true);
                    } else {
                        if (isEdit) {
                            // Delete the current server and re-add with new values.
                            mPrefs.removeServer(mPrefs.getSelectedServer());
                            mPrefs.setSelectedServer(serverName);
                        }
                        ((OnServerListChangeListener) getHost()).onServerAdded(false);
                    }

                    dismiss();
                }
            });
            Button neutralButton = self.getButton(Dialog.BUTTON_NEUTRAL);
            neutralButton.setTextColor(Color.RED);
        }

        if (mSelectedField != null) {
            switch (mSelectedField) {
                case EDIT_NAME:
                    mNameField.requestFocus();
                    break;
                case EDIT_API_KEY:
                    mAPIKeyField.requestFocus();
                    break;
                case EDIT_PORT:
                    mPortField.setSelected(true);
                    break;
                default:
                    mNameField.setSelected(true);
            }
        }
    }

    /*
        Option dialog to confirm deletion of server
     */
    private void confirmDelete(final OnServerListChangeListener host) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete this server?")
                .setMessage("Do you really want to delete this server?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        host.onServerDeleted();
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
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
        Responds to the activity result once a user has chosen a file from performFileSearch
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
                    mClearCertButton.setVisibility(View.VISIBLE);
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

    private void saveCertToServer(String server) throws IOException {
        if (mCertURI.equals("")) // Don't need to save a non-existent cert
            return;

        File saveDir = new File(getContext().getFilesDir() + "/" + server);
        if (!saveDir.exists()) {
            if (!saveDir.mkdir()) {
                throw new IOException("Certificate could not be saved.");
            }
        }

        File saveLocation = new File(getContext().getFilesDir() + "/" + server + "/" +
                mCertField.getText());
        try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(mCertURI))) {
            try (OutputStream out = new FileOutputStream(saveLocation)) {
                byte[] buffer = new byte[1024];
                int length;
                if(in != null) {
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                } else {
                    throw new IOException("Certificate could not be read.");
                }
            }
        }
        mCertURI = Uri.fromFile(saveLocation).toString();
    }

    @Override
    public void onStop() {
        if (mNameField.isFocused())
            mSelectedField = EDIT_NAME;
        if (mAPIKeyField.isFocused())
            mSelectedField = EDIT_API_KEY;
        if (mPortField.isFocused())
            mSelectedField = EDIT_PORT;
        super.onStop();
    }
}
