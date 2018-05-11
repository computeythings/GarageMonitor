package computeythings.piopener.async;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import javax.net.ssl.SSLSocket;

/**
 * Asynchronous thread for closing an open connection to avoid networking on main thread
 * <p>
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketClose extends AsyncTask<SSLSocket, Void, Void> {
    private static final String TAG = "SOCKET_CLOSE";
    private static final String SOCKET_CLOSE = "KILL";

    @Override
    protected Void doInBackground(SSLSocket... sockets) {
        if (sockets.length != 1 || sockets[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
            return null;
        }
        SSLSocket socket = sockets[0];
        try {
            OutputStream out = socket.getOutputStream();
            out.write(SOCKET_CLOSE.getBytes());
            out.flush(); // Force flush to send data
            socket.close(); // Close client side socket
        } catch (IOException e) {
            Log.w(TAG, "Server connection already closed.");
        }
        return null;
    }
}