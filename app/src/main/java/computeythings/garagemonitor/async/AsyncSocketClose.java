package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.util.Log;

import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Asynchronous thread for closing an open connection to avoid networking on main thread
 * <p>
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketClose extends AsyncTask<TCPSocketService, Void, Void> {
    private static final String TAG = "SOCKET_CLOSE";

    @Override
    protected Void doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
            return null;
        }
        tcpSocketServices[0].socketClose();
        return null;
    }
}