package computeythings.garagemonitor.async;

import android.content.Context;
import android.os.AsyncTask;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Created by bryan on 2/8/18.
 */

public class AsyncSocketRefresh extends AsyncTask<TCPSocketService, Void, Void> {
    private static final String TAG = "SOCKET_REFRESH";
    private WeakReference<SwipeRefreshLayout> mLayout;
    private WeakReference<Context> mContext;
    private boolean mSuccessfulRefresh;

    public AsyncSocketRefresh(Context context, SwipeRefreshLayout layout) {
        mContext = new WeakReference<>(context);
        mLayout = new WeakReference<>(layout);
    }

    @Override
    protected Void doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
        }
        mSuccessfulRefresh = tcpSocketServices[0].socketWrite(TCPSocketService.SEND_REFRESH);
        return null;
    }

    @Override
    protected void onPostExecute(Void completed) {
        mLayout.get().setRefreshing(false);
        if(!mSuccessfulRefresh)
            Toast.makeText(mContext.get(), "Could not reach server for refresh.",
                    Toast.LENGTH_LONG).show();
    }
}