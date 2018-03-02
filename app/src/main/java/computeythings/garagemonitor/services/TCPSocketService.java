package computeythings.garagemonitor.services;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import computeythings.garagemonitor.async.AsyncSocketClose;
import computeythings.garagemonitor.async.AsyncSocketCreator;
import computeythings.garagemonitor.interfaces.SocketCreatedListener;

/**
 * SSL Socket service used to connect to garage door opener server.
 * Requires a cert be stored in the res/raw resources directory.
 * <p>
 * Created by bryan on 2/6/18.
 */

public class TCPSocketService extends IntentService implements SocketCreatedListener{
    private static final String TAG = "SOCKET_SERVICE";
    public static final String SEND_REFRESH = "REFRESH";
    public static final String SOCKET_CLOSE = "KILL";
    public static final String GARAGE_OPEN = "OPEN_GARAGE";
    public static final String GARAGE_CLOSE = "CLOSE_GARAGE";
    public static final String SERVER_ADDRESS =
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
    public static final String CONNECTION_ERROR =
            "computeythings.garagemonitor.services.TCPSocketService.CONNECTION_ERROR";
    private String mServerAddress;
    private String mApiKey;
    private int mPort;
    private String mCertLocation;
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
        try {
            mServerAddress = intent.getStringExtra(SERVER_ADDRESS);
            mApiKey = intent.getStringExtra(API_KEY);
            mPort = intent.getIntExtra(PORT_NUMBER, -1);
            mCertLocation = intent.getStringExtra(CERT_ID);
        } catch (NullPointerException e) {
            Log.e(TAG, "Missing server components");
            e.printStackTrace();
        }

        // Open socket with new server properties
        socketOpen();

        // Create new socket polling thread
        mReceiverThread = new DataReceiver(mBroadcaster);
        mReceiverThread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !mServerAddress.equals(intent.getStringExtra(SERVER_ADDRESS)) ||
                !mApiKey.equals(intent.getStringExtra(API_KEY)) ||
                mPort != intent.getIntExtra(PORT_NUMBER, -1) ||
                !mCertLocation.equals(intent.getStringExtra(CERT_ID))) {
            Log.d(TAG, "Attempting to rebind to socket belonging to different server");
            return null;
        }

        return mBinder;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null)
            return;
        // Do not start if values are still not valid
        if (mServerAddress == null || mPort == -1 || mCertLocation == null || mApiKey == null) {
            Toast.makeText(this, "Invalid server values.", Toast.LENGTH_SHORT).show();
            stopSelf();
        }
    }

    /*
        Creates trust manager using self signed certs stored in raw resources directory
     */
    private SSLSocketFactory createSocketFactory() {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate ca;
            // Load ca from resource directory
            try (InputStream caInput = getContentResolver().openInputStream(Uri.parse(mCertLocation))) {
                ca = cf.generateCertificate(caInput);
            } catch (NullPointerException e) {
                Log.d(TAG, "Invalid cert given");
                e.printStackTrace();
                return null;
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
        Creates an SSL Socket connection to the server which the service was started with
     */
    public void socketOpen() {
        new AsyncSocketCreator(createSocketFactory(), this)
                .executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        mServerAddress, mPort + "", mApiKey);
    }

    public boolean isConnected() {
        return mSocketConnection != null && !mSocketConnection.isClosed();
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
            Log.w(TAG, "Error writing to connection on " + mServerAddress + ":" + mPort);
            // If socket is closed, attempt to reopen and resend message.
            return false;
        }
    }

    /*
        Send a close message to server and kill socket connection.
     */
    public void socketClose() {
        if (mSocketConnection != null && !mSocketConnection.isClosed()) {
            try {
                OutputStream out = mSocketConnection.getOutputStream();
                out.write(SOCKET_CLOSE.getBytes());
                out.flush(); // Force flush to send data
                mSocketConnection.close(); // Close client side socket
            } catch (IOException e) {
                Log.w(TAG, "Error writing to connection on " + mServerAddress);
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
        // allow rebind as long as socket is open
        return mSocketConnection != null && mSocketConnection.isConnected();
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

    @Override
    public void onSocketReady(SSLSocket socket, String errorMsg) {
        mSocketConnection = socket;
        if(mSocketConnection != null) {
            mReceiverThread = new DataReceiver(mBroadcaster);
            mReceiverThread.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, mSocketConnection);
        } else {
            Intent intent = new Intent(CONNECTION_ERROR);
            intent.putExtra(DATA, errorMsg);
            mBroadcaster.sendBroadcast(intent);
        }
    }

    /*
        Class handles bindings and this service as a binder in order to give access to its methods
     */
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
            SSLSocket socket = connection[0];
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                while (socket.isConnected() && !isCancelled()) {
                    data = in.readLine();
                    if (data != null && !data.equals("")) {
                        publishProgress(data);
                    } else {
                        socket.close();
                    }
                }
            } catch (IOException | NullPointerException e) {
                Log.w(TAG, "Disconnected from server");
                e.printStackTrace();
            }
            broadcastMessage(SERVERSIDE_DISCONNECT);
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
