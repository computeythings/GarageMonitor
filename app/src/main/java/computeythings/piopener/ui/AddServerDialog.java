package computeythings.piopener.ui;

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
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import computeythings.piopener.R;
import computeythings.piopener.preferences.ServerPreferences;

import static computeythings.piopener.preferences.ServerPreferences.NOTIFICATION_TIMER;
import static computeythings.piopener.preferences.ServerPreferences.NOTIFY_CLOSED;
import static computeythings.piopener.preferences.ServerPreferences.NOTIFY_CLOSING;
import static computeythings.piopener.preferences.ServerPreferences.NOTIFY_OPEN;
import static computeythings.piopener.preferences.ServerPreferences.NOTIFY_OPENING;
import static computeythings.piopener.preferences.ServerPreferences.SERVER_ADDRESS;
import static computeythings.piopener.preferences.ServerPreferences.SERVER_API_KEY;
import static computeythings.piopener.preferences.ServerPreferences.SERVER_CERT;
import static computeythings.piopener.preferences.ServerPreferences.SERVER_NAME;
import static computeythings.piopener.preferences.ServerPreferences.SERVER_PORT;

/**
 * Dialog used to add and edit server settings. Upon adding a server, a callback is sent to the
 * host UI activity.
 * <p>
 * Created by bryan on 2/10/18.
 */

public class AddServerDialog extends DialogFragment {
    private static final String TAG = "ADD_SERVER_DIALOG";
    public static final String EDIT_INFO = "EDIT_INFO";
    public static final String EDIT_NAME = "EDIT_NAME";
    public static final String EDIT_ADDRESS = "EDIT_ADDRESS";
    public static final String EDIT_API_KEY = "EDIT_API_KEY";
    public static final String EDIT_PORT = "EDIT_PORT";
    private static final String STATE_OPEN = "OPEN";
    private static final String STATE_OPENING = "OPENING";
    private static final String STATE_CLOSED = "CLOSED";
    private static final String STATE_CLOSING = "CLOSING";
    private static final int READ_REQUEST_CODE = 444;

    private ServerPreferences mPrefs;
    private String mSelectedField;
    private TextView mNameField;
    private TextView mAPIKeyField;
    private TextView mAddressField;
    private TextView mPortField;
    private TextView mCertField;
    private TextView mClearCertButton;
    private TextView mNotificationTimer;
    private CheckBox mOpenNotifications;
    private CheckBox mOpeningNotifications;
    private CheckBox mClosedNotifications;
    private CheckBox mClosingNotifications;
    private String mCertURI;
    private String mServerName;

    public interface OnServerListChangeListener {
        void onServerModify(String server);

        void onServerDeleted(String server);
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(final Bundle savedInstanceState) {
        Bundle args = getArguments();
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        LayoutInflater inflater = getActivity().getLayoutInflater();

        final View dialogLayout = inflater.inflate(R.layout.dialog_addserver, null);
        builder.setView(dialogLayout);
        final LinearLayout advanced = dialogLayout.findViewById(R.id.advanced_options);

        dialogLayout.findViewById(R.id.show_advanced).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (advanced.getVisibility() == View.GONE) {
                    advanced.setVisibility(View.VISIBLE);
                    ((Button) (view)).setText(R.string.basic);
                } else {
                    advanced.setVisibility(View.GONE);
                    ((Button) (view)).setText(R.string.advanced);
                }
            }
        });

        //TODO: implement timed notifications with preferences timer != 0
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
        mNotificationTimer = dialogLayout.findViewById(R.id.notification_timer);
        mOpenNotifications = dialogLayout.findViewById(R.id.open_notifications);
        mOpeningNotifications = dialogLayout.findViewById(R.id.opening_notifications);
        mClosedNotifications = dialogLayout.findViewById(R.id.closed_notifications);
        mClosingNotifications = dialogLayout.findViewById(R.id.closing_notifications);

        // upload cert file from local storage
        Button certSearchButton = dialogLayout.findViewById(R.id.cert_search_btn);
        certSearchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                performFileSearch();
            }
        });

        // args are null on add and initialized on edit
        if (args != null && args.getSerializable(EDIT_INFO) != null) {
            // Populate current values
            HashMap serverInfo = (HashMap) args.getSerializable(EDIT_INFO);
            mServerName = (String) serverInfo.get(SERVER_NAME);
            mNameField.setText(mServerName);
            mAddressField.setText((String) serverInfo.get(SERVER_ADDRESS));
            mAPIKeyField.setText((String) serverInfo.get(SERVER_API_KEY));
            mPortField.setText((String) serverInfo.get(SERVER_PORT));
            mCertURI = (String) serverInfo.get(SERVER_CERT);
            // If we don't have a saved cert location, it's because there is no self-signed cert
            if (mCertURI.equals(""))
                mCertField.setHint("N/A");
            else {
                // Just show the file name. It's cleaner that way.
                String certName = mCertURI.substring(mCertURI.lastIndexOf(File.separator) + 1);
                mCertField.setText(certName);
                mClearCertButton.setVisibility(View.VISIBLE);
            }
            mOpenNotifications.setChecked(Boolean.valueOf((String) serverInfo.get(NOTIFY_OPEN)));
            mOpeningNotifications.setChecked(Boolean.valueOf((String) serverInfo.get(NOTIFY_OPENING)));
            mClosedNotifications.setChecked(Boolean.valueOf((String) serverInfo.get(NOTIFY_CLOSED)));
            mClosingNotifications.setChecked(Boolean.valueOf((String) serverInfo.get(NOTIFY_CLOSING)));

            long notificationTimer = Long.parseLong((String) serverInfo.get(NOTIFICATION_TIMER));
            if (notificationTimer > 0)
                mNotificationTimer.setText(String.valueOf(notificationTimer));

            // Change edit buttons and give Delete option
            builder.setNeutralButton(R.string.delete, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    confirmDelete((OnServerListChangeListener) getHost());
                }
            });
            builder.setPositiveButton(R.string.update, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    /* no implementation as it is overridden in onResume() */
                }
            });
        } else {
            builder.setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    /* no implementation as it is overridden in onResume() */
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

        // we have to define the onClick method for the positive button here in order to overwrite
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
                    Set<String> monitoredStates = new HashSet<>();
                    String timer = mNotificationTimer.getText().toString().trim();
                    if (timer.equals(""))
                        timer = "0";

                    if (serverName.equals(""))
                        serverName = serverAddress;
                    if (serverPort.equals(""))
                        serverPort = "4444";
                    if (certLocation.equals("") && mCertURI == null)
                        mCertURI = certLocation;
                    if (mOpenNotifications.isChecked())
                        monitoredStates.add(STATE_OPEN);
                    if (mOpeningNotifications.isChecked())
                        monitoredStates.add(STATE_OPENING);
                    if (mClosedNotifications.isChecked())
                        monitoredStates.add(STATE_CLOSED);
                    if (mClosingNotifications.isChecked())
                        monitoredStates.add(STATE_CLOSING);

                    if (serverAddress.length() <= 0 || serverApiKey.length() <= 0) {
                        if (serverApiKey.length() <= 0)
                            mAPIKeyField.setError("This server requires an API key.");
                        if (serverAddress.length() <= 0)
                            mAddressField.setError("You must add a server.");
                        return;
                    }

                    try {
                        // attempt to save any uploaded cert locally
                        saveCertToServer(serverName);
                    } catch (IOException e) {
                        mCertField.setError("Error accessing certificate");
                        e.printStackTrace();
                        return;
                    }

                    final OnServerListChangeListener host = (OnServerListChangeListener) getHost();

                    if (mServerName == null && mPrefs.getServerList().contains(serverName)) {
                        new AlertDialog.Builder(getContext())
                                .setTitle("Server Name Error")
                                .setMessage("A server with that name already exists!")
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setNegativeButton(android.R.string.ok, null).show();
                    } else {
                        // If the key was changed, remove the key/value pair
                        if (mServerName != null && !serverName.equals(mServerName))
                            host.onServerDeleted(mServerName);
                        // store new server info in preferences
                        mPrefs.addServer(serverName, serverAddress, serverApiKey,
                                Integer.parseInt(serverPort), mCertURI);
                        mPrefs.setNotifications(serverName, monitoredStates, Long.parseLong(timer));

                        // send callback to host activity that a server was added
                        ((OnServerListChangeListener) getHost()).onServerModify(serverName);

                        dismiss();
                    }
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
                        host.onServerDeleted(mServerName);
                    }
                })
                .setNegativeButton(android.R.string.no, null).show();
    }

    /*
        Search file browser on local storage
     */
    private void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        // filter to only show results that can be "opened", such as a
        // file (as opposed to a list of contacts or timezones)
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // allow any file type as cert
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
        if (mCertURI.equals("")) // don't need to save a non-existent cert
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
                if (in != null) {
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
        if (mAddressField.isFocused())
            mSelectedField = EDIT_ADDRESS;
        super.onStop();
    }
}
