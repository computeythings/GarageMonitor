package computeythings.garagemonitor;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;


/**
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketCreator extends AsyncTask<String, Void, SSLSocket> {
    private static final String TAG = "SOCKET_CREATOR";
    private SSLSocketFactory mSocketFactory;

    AsyncSocketCreator(SSLSocketFactory socketFactory) {
        mSocketFactory = socketFactory; // created from self-signed cert
    }

    @Override
    protected SSLSocket doInBackground(String... serverInfo) {
        SSLSocket socket = null;
        String server = serverInfo[0];
        int port = Integer.parseInt(serverInfo[1]);
        String api = serverInfo[2];

        try {
            Log.d(TAG, "\n\n\ncreating socket\n\n\n");
            Socket raw = new Socket(server, port);
            socket = (SSLSocket) mSocketFactory.createSocket(raw, server, port, false);
            OutputStream out = socket.getOutputStream();
            out.write(api.getBytes());
            out.write(TCPSocketService.SEND_REFRESH.getBytes());
        } catch (IOException e) {
            Log.d(TAG, "Error creating socket to " + server + " on socket " + port);
            e.printStackTrace();
        }
        return socket;
    }
}
