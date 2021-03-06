package computeythings.piopener.async;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
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

import computeythings.piopener.interfaces.SocketCreatedListener;
import computeythings.piopener.interfaces.SocketResultListener;
import computeythings.piopener.preferences.ServerPreferences;

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
    private AsyncSocketReader reader;
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
    public static SocketConnector fromInfo(HashMap<String, String> serverInfo, Context context,
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
    public void socketWrite(final String message) {
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
                // If we couldn't connect to the socket, do a continuous reconnect attempt
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                socketReconnect(UI_REQUEST_DELAY);
                Toast.makeText(context, "Attempting to reconnect to server...",
                        Toast.LENGTH_LONG).show();
                queueMessage(message); // Save message and send again on reconnect
                return;
            }
        }

        new AsyncSocketWriter(message, socket, uiListener).start();
    }

    /*
        Attempts to gracefully close the SSLSocket connection.
     */
    public void socketClose() {
        if (sender != null)
            sender.removeCallbacksAndMessages(null); // stop trying to reconnect
        if (reader != null)
            reader.cancel(true);

        if (isDisconnected())
            return; //what is dead may never die//

        new AsyncSocketClose().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket);
        isConnected = false;
    }

    public boolean isDisconnected() {
        return !isConnected;
    }

    @Override
    public String toString() {
        return name;
    }

    /*
        Run once the SocketCreator run at creation has completed creation.
        Will pass either a socket and a message containing the FCM reference ID or a null value
        and an error message.
     */
    @Override
    public void onSocketReady(SSLSocket socket, String message) {
        this.socket = socket;
        if (socket != null && !socket.isClosed()) {
            isConnected = true;
            reader = new AsyncSocketReader(uiListener);
            reader.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, socket);
            // save the reference ID to app preferences
            new ServerPreferences(context).setServerRefId(name, message);
            // write the last queued message if one exists
            if (queue != null) {
                socketWrite(queue);
            }
        } else {
            isConnected = false;
            if (message != null) {
                uiListener.onSocketResult(false);
            }

            // retry socket until successful connect
            socketReconnect(BACKGROUND_REQUEST_DELAY);
        }
        // we don't want to hold the queue too long. Clear whether the socket connected or not.
        queue = null;
        uiListener.onSocketResult(true);
    }
}
