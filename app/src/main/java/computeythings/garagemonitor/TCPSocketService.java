package computeythings.garagemonitor;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
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

/**
 * SSL Socket service used to connect to garage door opener server.
 * Requires a cert be stored in the res/raw resources directory.
 * <p>
 * Created by bryan on 2/6/18.
 */

public class TCPSocketService extends Service {
    private static final String TAG = "SOCKET_SERVICE";
    public static final String SEND_REFRESH = "REFRESH";
    public static final String SOCKET_CLOSE = "KILL";
    public static final String GARAGE_OPEN = "OPEN_GARAGE";
    public static final String GARAGE_CLOSE = "CLOSE_GARAGE";
    public static final String SERVER_NAME =
            "computeythings.garagemonitor.TCPSocketService.SERVER";
    public static final String API_KEY =
            "computeythings.garagemonitor.TCPSocketService.API_KEY";
    public static final String PORT_NUMBER =
            "computeythings.garagemonitor.TCPSocketService.PORT";
    public static final String CERT_ID =
            "computeythings.garagemonitor.TCPSocketService.CERT";
    public static final String DATA_RECEIVED =
            "computeythings.garagemonitor.TCPSocketService.NEW_DATA";
    public static final String DATA =
            "computeythings.garagemonitor.TCPSocketService.DATA_PAYLOAD";
    public static final String SERVERSIDE_DISCONNECT =
            "computeythings.garagemonitor.TCPSocketService.SERVER_DISCONNECT";
    private String mServerName;
    private String mApiKey;
    private int mPort;
    private int mCertID;
    private SSLSocket mSocketConnection;
    private LocalBroadcastManager mBroadcaster;
    private DataReceiver mReceiverThread;
    private final SocketServiceBinder mBinder = new SocketServiceBinder();

    @Override
    public void onCreate() {
        super.onCreate();
        mBroadcaster = LocalBroadcastManager.getInstance(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        // Only update values if the socket has not already been bound
        if (mSocketConnection == null || !mSocketConnection.isBound()) {
            mServerName = intent.getStringExtra(SERVER_NAME);
            mApiKey = intent.getStringExtra(API_KEY);
            mPort = intent.getIntExtra(PORT_NUMBER, -1);
            mCertID = intent.getIntExtra(CERT_ID, -1);
        }

        // Do not bind if values are still not valid
        if (mServerName == null || mPort == -1 || mCertID == -1 || mApiKey == null) {
            Toast.makeText(this, "Invalid server values!", Toast.LENGTH_SHORT).show();
            return null;
        }

        socketOpen();
        return mBinder;
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

        mReceiverThread = new DataReceiver(mBroadcaster);
        mReceiverThread.execute(mSocketConnection);
    }

    /*
        Manually request updated data from server.
     */
    public void socketRefresh() {
        try {
            OutputStream out = mSocketConnection.getOutputStream();
            out.write(SEND_REFRESH.getBytes());
            out.flush(); // Force flush to send data
        } catch (IOException | NullPointerException e) {
            Log.w(TAG, "Error writing to connection on " + mServerName);
            socketOpen();
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
            Log.d(TAG, "Socket message sent");
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
            mReceiverThread.cancel(true); // Kill polling on socket
        }
    }

    /*
        On unbind the socket connection stays open but the thread reading input is killed
     */
    @Override
    public boolean onUnbind(Intent intent) {
        mReceiverThread.cancel(true);
        mReceiverThread = null; // kill dead thread
        return true;
    }

    @Override
    public void onDestroy() {
        socketClose();
        super.onDestroy();
    }

    class SocketServiceBinder extends Binder {
        TCPSocketService getService() {
            return TCPSocketService.this;
        }
    }

    /*
        Separates polling for data in its own thread
     */
    private static class DataReceiver extends AsyncTask<SSLSocket, String, Void> {
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

                while (socket.isConnected()) {
                    data = in.readLine();
                    if (data != null && !data.equals("")) {
                        publishProgress(data);
                    } else {
                        socket.close();
                        throw new NullPointerException("Null socket connection to server");
                    }
                }
            } catch (IOException | NullPointerException e) {
                Log.w(TAG, "Disconnected from server");
                broadcastMessage(SERVERSIDE_DISCONNECT);
                e.printStackTrace();
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
