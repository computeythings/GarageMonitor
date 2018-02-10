package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.util.Log;

import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketWriter extends AsyncTask<TCPSocketService, Void, Boolean> {
    private static final String TAG = "SOCKET_WRITER";
    private String mMessage;

    public AsyncSocketWriter(String message) {
        mMessage = message;
    }

    @Override
    protected Boolean doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
            return false;
        }
        return tcpSocketServices[0].socketWrite(mMessage);
    }
}