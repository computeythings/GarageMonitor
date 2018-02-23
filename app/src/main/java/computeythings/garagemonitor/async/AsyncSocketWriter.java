package computeythings.garagemonitor.async;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import computeythings.garagemonitor.services.TCPSocketService;

/**
 * Created by bryan on 2/6/18.
 */

public class AsyncSocketWriter extends AsyncTask<TCPSocketService, Void, Void> {
    private static final String TAG = "SOCKET_WRITER";
    private WeakReference<Context> mContext;
    private String mMessage;
    private boolean mSuccessfulWrite;

    public AsyncSocketWriter(Context context, String message) {
        mContext = new WeakReference<>(context);
        mMessage = message;
    }

    @Override
    protected Void doInBackground(TCPSocketService... tcpSocketServices) {
        if (tcpSocketServices.length != 1 || tcpSocketServices[0] == null) {
            Log.e(TAG, "Incorrect SocketHandler parameters");
        }
        mSuccessfulWrite = tcpSocketServices[0].socketWrite(mMessage);
        return null;
    }

    @Override
    protected void onPostExecute(Void completed) {
        if(!mSuccessfulWrite)
            Toast.makeText(mContext.get(), "Could not reach server.",
                    Toast.LENGTH_LONG).show();
    }
}
