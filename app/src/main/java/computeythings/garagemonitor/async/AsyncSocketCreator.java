package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import computeythings.garagemonitor.services.TCPSocketService;


/**
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketCreator extends AsyncTask<String, Void, SSLSocket> {
    private static final String TAG = "SOCKET_CREATOR";
    private SSLSocketFactory mSocketFactory;

    public AsyncSocketCreator(SSLSocketFactory socketFactory) {
        if (socketFactory != null)
            mSocketFactory = socketFactory; // created from self-signed cert
        else
            mSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
    }

    @Override
    protected SSLSocket doInBackground(String... serverInfo) {
        SSLSocket socket = null;
        String server = serverInfo[0];
        int port = Integer.parseInt(serverInfo[1]);
        String api = serverInfo[2];

        try {
            Log.i(TAG, "Creating socket");
            Socket raw = new Socket(server, port);
            socket = (SSLSocket) mSocketFactory.createSocket(raw, server, port, false);
            OutputStream out = socket.getOutputStream();
            out.write(api.getBytes());
            out.write(TCPSocketService.SEND_REFRESH.getBytes());
        } catch (IOException e) {
            Log.e(TAG, "Error creating socket to " + server + " on socket " + port);
            e.printStackTrace();
        }
        return socket;
    }
}
