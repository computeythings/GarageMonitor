package computeythings.garagemonitor;

import android.os.AsyncTask;
import android.util.Log;

/**
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketRefresh extends AsyncTask<TCPSocketService, Void, Void> {
    private static final String TAG = "SOCKET_REFRESH";

    @Override
    protected Void doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
            return null;
        }
        tcpSocketServices[0].socketRefresh();
        return null;
    }
}
