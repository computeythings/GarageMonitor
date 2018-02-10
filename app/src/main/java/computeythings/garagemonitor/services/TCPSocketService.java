package computeythings.garagemonitor.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;

import computeythings.garagemonitor.async.AsyncSocketClose;
import computeythings.garagemonitor.async.AsyncSocketCreator;

/**
 * SSL Socket service used to connect to garage door opener server.
 * Requires a cert be stored in the res/raw resources directory.
 * <p>
 * Created by bryan on 2/6/18.
 */

public class TCPSocketService extends IntentService {
    private static final String TAG = "SOCKET_SERVICE";
    public static final String SEND_REFRESH = "REFRESH";
    public static final String SOCKET_CLOSE = "KILL";
    public static final String GARAGE_OPEN = "OPEN_GARAGE";
    public static final String GARAGE_CLOSE = "CLOSE_GARAGE";
    public static final String SERVER_NAME =
            "computeythings.garagemonitor.services.TCPSocketService.SERVER";
    public static final String API_KEY =
            "computeythings.garagemonitor.services.TCPSocketService.API_KEY";
    public static final String PORT_NUMBER =
            "computeythings.garagemonitor.services.TCPSocketService.PORT";
    public static final String CERT_ID =
            "computeythings.garagemonitor.services.TCPSocketService.CERT";
    public static final String DATA_RECEIVED =
            "computeythings.garagemonitor.services.TCPSocketService.NEW_DATA";
    public static final String DATA =
            "computeythings.garagemonitor.services.TCPSocketService.DATA_PAYLOAD";
    public static final String SERVERSIDE_DISCONNECT =
            "computeythings.garagemonitor.services.TCPSocketService.SERVER_DISCONNECT";
    private String mServerName;
    private String mApiKey;
    private int mPort;
    private int mCertID;
    private SSLSocket mSocketConnection;
    private LocalBroadcastManager mBroadcaster;
    private DataReceiver mReceiverThread;
    private final SocketServiceBinder mBinder = new SocketServiceBinder();

    public TCPSocketService() {
        super("TCPSocketService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "New socket service");
        mBroadcaster = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Load in server properties
        mServerName = intent.getStringExtra(SERVER_NAME);
        mApiKey = intent.getStringExtra(API_KEY);
        mPort = intent.getIntExtra(PORT_NUMBER, -1);
        mCertID = intent.getIntExtra(CERT_ID, -1);
        // Open socket with new server properties
        socketOpen();

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!mServerName.equals(intent.getStringExtra(SERVER_NAME)) ||
        !mApiKey.equals(intent.getStringExtra(API_KEY)) ||
        mPort != intent.getIntExtra(PORT_NUMBER, -1) ||
        mCertID != intent.getIntExtra(CERT_ID, -1)) {
            Log.d(TAG, "Attempting to rebind to socket belonging to different server");
            return null;
        }
        // Create new socket polling thread
        mReceiverThread = new DataReceiver(mBroadcaster);
        mReceiverThread.execute(mSocketConnection);
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Create new socket polling thread
        mReceiverThread = new DataReceiver(mBroadcaster);
        mReceiverThread.execute(mSocketConnection);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent == null)
            return;
        // Do not start if values are still not valid
        if (mServerName == null || mPort == -1 || mCertID == -1 || mApiKey == null) {
            Toast.makeText(this, "Invalid server values.", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    /*
        Creates trust manager using self signed certs stored in raw resources directory
     */
    private SSLContext createTrustManager() {
        SSLContext context = null;
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            // Load ca from resource directory
            try (InputStream caInput = getResources().openRawResource(mCertID)) {
                ca = cf.generateCertificate(caInput);
            }

            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            // Create a TrustManager that trusts the CAs in our KeyStore
            TrustManagerFactory tmf = TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);

            // Create an SSLContext that uses our TrustManager
            context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), new SecureRandom());
        } catch (IOException | KeyManagementException | KeyStoreException |
                NoSuchAlgorithmException | CertificateException e) {
            e.printStackTrace();
            Log.e(TAG, "Error with trust manager");
        }
        return context;
    }

    /*
        Creates an SSL Socket connection to the server which the service was started with
     */
    private void socketOpen() {
        try {
            mSocketConnection = new AsyncSocketCreator(createTrustManager().getSocketFactory())
                    .execute(mServerName, mPort + "", mApiKey).get();
        } catch (InterruptedException | ExecutionException e) {
            Log.e(TAG, "Error creating socket");
        }
    }

    /*
        Write message to server over socket. Returns false if the message can't be sent.
     */
    public boolean socketWrite(String message) {
        try {
            OutputStream out = mSocketConnection.getOutputStream();
            out.write(message.getBytes());
            out.flush(); // Force flush to send data
            return true;
        } catch (IOException | NullPointerException e) {
            Log.w(TAG, "Error writing to connection on " + mServerName);
            socketOpen(); // If socket is closed, data should be refreshed on reconnect.
        }
        return false;
    }

    /*
        Send a close message to server and kill socket connection.
     */
    public void socketClose() {
        Log.d(TAG, "Closing socket connection");
        if (mSocketConnection != null && !mSocketConnection.isClosed()) {
            try {
                OutputStream out = mSocketConnection.getOutputStream();
                out.write(SOCKET_CLOSE.getBytes());
                out.flush(); // Force flush to send data
                mSocketConnection.close(); // Close client side socket
            } catch (IOException e) {
                Log.w(TAG, "Error writing to connection on " + mServerName);
            }
            mSocketConnection = null; // Delete dead socket
            if (mReceiverThread != null) {
                mReceiverThread.cancel(true); // Kill polling on socket
                mReceiverThread = null;
            }
        }
    }

    /*
        On unbind the socket connection stays open but the thread reading input is killed
     */
    @Override
    public boolean onUnbind(Intent intent) {
        mReceiverThread.cancel(true);
        mReceiverThread = null; // kill dead thread
        return mSocketConnection.isConnected(); // allow rebind as long as socket is open
    }

    /*
        Run when the service is no longer bound to any listeners
     */
    @Override
    public void onDestroy() {
        // Close the current socket connection
        new AsyncSocketClose().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, this);
        super.onDestroy();
    }

    public class SocketServiceBinder extends Binder {
        public TCPSocketService getService() {
            return TCPSocketService.this;
        }
    }

    /*
        Separates polling for data in its own thread
     */
    private static class DataReceiver extends AsyncTask<SSLSocket, String, Void> {
        private static final String TAG = "DATA_RECEIVER_THREAD";
        LocalBroadcastManager mBroadcaster;

        DataReceiver(LocalBroadcastManager broadcaster) {
            this.mBroadcaster = broadcaster;
        }

        /*
            Continually stream data from the server and update when data is received
         */
        @Override
        protected Void doInBackground(SSLSocket... connection) {
            BufferedReader in;
            String data;
            try {
                SSLSocket socket = connection[0];
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                while (socket.isConnected() && !isCancelled()) {
                    data = in.readLine();
                    if (data != null && !data.equals("")) {
                        publishProgress(data);
                    } else {
                        throw new NullPointerException("Null socket connection to server");
                    }
                }
            } catch (IOException | NullPointerException e) {
                Log.w(TAG, "Disconnected from server");
                broadcastMessage(SERVERSIDE_DISCONNECT);
            }
            return null;
        }

        /*
            Sends broadcast with intent TCPSocketService.DATA_RECEIVED and content @msg
         */
        private void broadcastMessage(String msg) {
            Intent intent = new Intent(DATA_RECEIVED);
            intent.putExtra(DATA, msg);
            this.mBroadcaster.sendBroadcast(intent);
        }

        /*
            Update main activity with data received
         */
        @Override
        protected void onProgressUpdate(String... data) {
            String msg = data[0];
            if (msg.equals("Connected."))
                return;
            broadcastMessage(data[0]);
        }
    }
}
