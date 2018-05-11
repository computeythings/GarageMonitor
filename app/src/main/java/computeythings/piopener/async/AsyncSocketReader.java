package computeythings.piopener.async;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.net.ssl.SSLSocket;

import computeythings.piopener.interfaces.SocketResultListener;
import computeythings.piopener.preferences.ServerPreferences;

public class AsyncSocketReader extends AsyncTask<SSLSocket, String, Void> {
    private static final String TAG = "SOCKET_READER";
    private static final String STATE = "STATE";
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
            JSONObject json;
            while (socket.isConnected() && !isCancelled()) {
                data = in.readLine();
                if (data != null && !data.equals("")) {
                    try {
                        json = new JSONObject(data);
                        publishProgress(json.getString(STATE));
                    } catch (JSONException e) {
                        Log.e(TAG, "Could not process data received over TCP socket: " + data);
                        e.printStackTrace();
                    }
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
