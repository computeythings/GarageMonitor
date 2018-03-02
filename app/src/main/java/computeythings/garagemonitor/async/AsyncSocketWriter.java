package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.util.Log;

import computeythings.garagemonitor.interfaces.SocketResultListener;
import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Asynchronous thread for writing to a socket connection to avoid networking on main thread
 * <p>
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketWriter extends AsyncTask<TCPSocketService, Void, Boolean> {
    private static final String TAG = "SOCKET_WRITER";
    private SocketResultListener mListener;
    private String mMessage;

    public AsyncSocketWriter(String message, SocketResultListener listener) {
        mListener = listener;
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

    @Override
    protected void onPostExecute(Boolean successfulWrite) {
        mListener.onSocketResult(successfulWrite);
    }
}
