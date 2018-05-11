package computeythings.piopener.async;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import computeythings.piopener.interfaces.SocketCreatedListener;


/**
 * Asynchronous thread for creating new socket connections to avoid networking on main thread
 * <p>
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketCreator extends AsyncTask<String, Void, SSLSocket> {
    private static final String TAG = "SOCKET_CREATOR";
    private final SSLSocketFactory mSocketFactory;
    private final SocketCreatedListener mListener;
    private String dataExtra;

    public AsyncSocketCreator(SSLSocketFactory socketFactory,
                              SocketCreatedListener listener) {
        if (socketFactory != null)
            mSocketFactory = socketFactory; // created from self-signed cert
        else
            mSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        mListener = listener;
    }

    @Override
    protected SSLSocket doInBackground(String... serverInfo) {
        SSLSocket socket = null;
        String name = serverInfo[0];
        String server = serverInfo[1];
        String api = serverInfo[2];
        int port = Integer.parseInt(serverInfo[3]);

        try {
            Log.i(TAG, "Creating socket");
            Socket raw = new Socket(server, port);
            socket = (SSLSocket) mSocketFactory.createSocket(raw, server, port, true);
            // connect to the new SSL socket and send the API Key as verification
            OutputStream out = socket.getOutputStream();
            out.write(api.getBytes());
            out.close();
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            do {
                dataExtra = in.readLine();
            } while (dataExtra == null || dataExtra.equals(""));
            in.close();
        } catch (IOException e) {
            Log.e(TAG, "Error creating socket to " + server + " on socket " + port);
            e.printStackTrace();
        }


        try {
            if (!verifyHost(server, socket)) { // this will only happen if the socket is null
                dataExtra = "Could not reach server";
            }
        } catch (SSLHandshakeException e) {
            e.printStackTrace();

            try {
                if (socket != null)
                    socket.close();
            } catch (IOException ex) {
                Log.e(TAG, "Could not close socket.");
                ex.printStackTrace();
            }
            dataExtra = "Could not verify hostname.\nPossible Man-in-the-middle attack!";
        }

        return socket;
    }

    private boolean verifyHost(String server, SSLSocket socket) throws SSLHandshakeException {
        if (socket == null) // cannot verify host if socket is null
            return false;

        // if the address is local we'll go ahead and skip hostname verification.
        // if you've got malicious hosts within your local network you've got other problems.
        try {
            if (InetAddress.getByName(server).isSiteLocalAddress())
                return true;
        } catch (UnknownHostException ignored) {
        }

        // hostname verification
        HostnameVerifier verifier = HttpsURLConnection.getDefaultHostnameVerifier();
        SSLSession session = socket.getSession();
        if (!verifier.verify(server, session)) {
            try {
                throw new SSLHandshakeException("Expected " + server + ",  found " +
                        session.getPeerPrincipal());
            } catch (SSLPeerUnverifiedException e) {
                throw new SSLHandshakeException("Expected " + server + ",  found " +
                        session.getPeerHost());
            }
        }
        return true;
    }

    @Override
    protected void onPostExecute(SSLSocket socket) {
        if (mListener != null)
            mListener.onSocketReady(socket, dataExtra);
    }
}
