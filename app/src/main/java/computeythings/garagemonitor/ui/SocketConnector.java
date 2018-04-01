package computeythings.garagemonitor.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import computeythings.garagemonitor.async.AsyncSocketClose;
import computeythings.garagemonitor.async.AsyncSocketCreator;
import computeythings.garagemonitor.interfaces.SocketCreatedListener;
import computeythings.garagemonitor.interfaces.SocketResultListener;
import computeythings.garagemonitor.preferences.ServerPreferences;
import computeythings.garagemonitor.services.FirestoreListenerService;

/**
 * Representation and interface for SSLSocket connections
 * <p>
 * Created by bryan on 3/21/18.
 */

public class SocketConnector implements SocketCreatedListener {
    private static final String TAG = "SOCKET_CONNECTOR";
    public static final String SEND_REFRESH = "REFRESH";
    public static final String GARAGE_OPEN = "OPEN_GARAGE";
    public static final String GARAGE_CLOSE = "CLOSE_GARAGE";
    private static final int UI_REQUEST_DELAY = 3000;
    private static final int BACKGROUND_REQUEST_DELAY = 60000;

    private SocketResultListener uiListener;
    private String queue;
    private Context context;
    private SSLSocket socket;
    private String name;
    private String address;
    private String apiKey;
    private int port;
    private String cert;
    private boolean isConnected;
    private Handler sender;

    /*
        Creates a new SocketConnector from A JSON formatted String
        along with the Context and LocalBroadcastManager.
     */
    static SocketConnector fromInfo(HashMap<String, String> serverInfo, Context context,
                                    SocketResultListener uiListener) {
        String name = serverInfo.get(ServerPreferences.SERVER_NAME);
        String address = serverInfo.get(ServerPreferences.SERVER_ADDRESS);
        String apiKey = serverInfo.get(ServerPreferences.SERVER_API_KEY);
        int port = Integer.parseInt(serverInfo.get(ServerPreferences.SERVER_PORT));
        String cert = serverInfo.get(ServerPreferences.SERVER_CERT);
        return new SocketConnector(name, address, apiKey, port, cert, context, uiListener);
    }

    private SocketConnector(String name, String address, String apiKey, int port, String cert,
                            Context context, SocketResultListener uiListener) {
        this.name = name;
        this.address = address;
        this.apiKey = apiKey;
        this.port = port;
        this.cert = cert;
        this.context = context;
        this.uiListener = uiListener;

        this.isConnected = false;
        this.sender = new Handler();
        socketConnect();
    }

    /*
        Creates trust manager using self signed certs stored in raw resources directory
        Returns null if a cert location does not exist (i.e. a self-signed cert is not used)
     */
    private SSLSocketFactory createSocketFactory(String cert) {
        if (cert == null || cert.equals(""))
            return null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            // load ca from resource directory
            try (InputStream caInput = context.getContentResolver().openInputStream(Uri.parse(cert))) {
                ca = cf.generateCertificate(caInput);
            } catch (NullPointerException e) {
                Log.d(TAG, "Invalid cert given");
                e.printStackTrace();
                return null;
            }

            // create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // create a TrustManager that trusts the CAs in our KeyStore
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // create an SSLContext that uses our TrustManager
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), new SecureRandom());
            return context.getSocketFactory();
        } catch (IOException | KeyManagementException | KeyStoreException |
                NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
            Log.e(TAG, "Error with trust manager");
        }
        return null;
    }

    /*
        Attempts to create an SSL Socket connection to the specified server
     */
    private void socketConnect() {
        new AsyncSocketCreator(createSocketFactory(cert), this)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                        name, address, apiKey, port + "");
    }

    /*
        If socket is not connect, reconnect.
     */
    private void socketReconnect(int delay) {
        sender.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isDisconnected()) {
                    socketConnect();
                }
            }
        }, delay);
    }

    /*
        Stores a single message queue to write the last message the user sent on reconnect.
     */
    private void queueMessage(String message) {
        queue = message;
    }

    /*
        Write message to server over socket. Returns false if the message can't be sent.
     */
    void socketWrite(final String message) {
        if (isDisconnected()) {
            try {
                // attempt immediate socket reconnect
                socket = new AsyncSocketCreator(createSocketFactory(cert), null)
                        .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                name, address, apiKey, port + "")
                        .get(300, TimeUnit.MILLISECONDS);
                if (socket == null)
                    throw new InterruptedException("null socket received.");
                else {
                    onSocketReady(socket, null);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                socketReconnect(UI_REQUEST_DELAY);
                Toast.makeText(context, "Attempting to reconnect to server...",
                        Toast.LENGTH_LONG).show();
                queueMessage(message);
                return;
            }
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean success;
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(message.getBytes());
                    out.flush(); // Force flush to send data
                    success = true;
                } catch (IOException | NullPointerException e) {
                    Log.w(TAG, "Error writing message " + message);
                    // If socket is closed, attempt to reopen and resend message.
                    success = false;
                }
                if (uiListener != null)
                    uiListener.onSocketResult(success);
            }
        }).start();
    }

    /*
        Attempts to gracefully close the SSLSocket connection.
     */
    void socketClose() {
        if (isDisconnected())
            return; //what is dead may never die//

        new AsyncSocketClose().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket);
        isConnected = false;
    }

    private boolean isDisconnected() {
        return !isConnected;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public void onSocketReady(SSLSocket socket, String errorMsg) {
        this.socket = socket;
        if (socket != null) {
            isConnected = socket.isConnected();

            // write the last queued message if one exists
            if (queue != null) {
                socketWrite(queue);
            }
        } else {
            if (errorMsg != null) {
                uiListener.onSocketResult(false);
            }

            // retry socket until successful connect
            socketReconnect(BACKGROUND_REQUEST_DELAY);
        }
        // we don't want to hold the queue too long. Clear whether the socket connected or not.
        queue = null;
    }

    /*
        Run once server document reference ID is received.
        Update Firestore listener and subscribe to document.
     */
    @Override
    public void onSocketData(String message) {
        if (message == null)
            return;

        // save the reference ID to app preferences
        new ServerPreferences(context).setServerRefid(name, message);

        // this should be handled by our intent service and subscribe to the document
        Intent updateRefIdIntent = new Intent(context, FirestoreListenerService.class);
        updateRefIdIntent.setAction(FirestoreListenerService.FOLLOW_SERVER);
        updateRefIdIntent.putExtra(ServerPreferences.SERVER_NAME, name);
        updateRefIdIntent.putExtra(ServerPreferences.SERVER_REFID, message);
        context.startService(updateRefIdIntent);
    }
}
