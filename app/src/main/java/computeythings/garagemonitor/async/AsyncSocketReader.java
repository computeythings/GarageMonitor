package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.net.ssl.SSLSocket;

import computeythings.garagemonitor.interfaces.SocketResultListener;

public class AsyncSocketReader extends AsyncTask<SSLSocket, String, Void> {
    private static final String TAG = "SOCKET_READER";
    private SocketResultListener uiListener;

    AsyncSocketReader(SocketResultListener uiListener) {
        this.uiListener = uiListener;
    }

    @Override
    protected Void doInBackground(SSLSocket... sslSockets) {
        SSLSocket socket = sslSockets[0];
        if(socket == null) {
            Log.e(TAG, "received invalid socket to read.");
            return null;
        }
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String data;
            while (socket.isConnected() && !isCancelled()) {
                data = in.readLine();
                if (data != null && !data.equals("")) {
                    publishProgress(data);
                }
            }
        } catch (IOException | NullPointerException e) {
            Log.e(TAG, "Error reading from socket " + socket.getInetAddress());
            e.printStackTrace();
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(String... data) {
        String msg = data[0];
        uiListener.onSocketData(msg);
    }
}
