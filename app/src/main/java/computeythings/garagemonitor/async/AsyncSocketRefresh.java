package computeythings.garagemonitor.async;

import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;

import java.lang.ref.WeakReference;

import computeythings.garagemonitor.interfaces.SocketResultListener;
import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Created by bryan on 2/8/18.
 */

public class AsyncSocketRefresh extends AsyncTask<TCPSocketService, Void, Boolean> {
    private static final String TAG = "SOCKET_REFRESH";
    private SocketResultListener mListener;

    public AsyncSocketRefresh(SocketResultListener listener) {
        mListener = listener;
    }

    @Override
    protected Boolean doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
            return false;
        }
        return tcpSocketServices[0].socketWrite(TCPSocketService.SEND_REFRESH);
    }

    @Override
    protected void onPostExecute(Boolean successfulRefresh) {
        mListener.onSocketResult(successfulRefresh);
    }
}