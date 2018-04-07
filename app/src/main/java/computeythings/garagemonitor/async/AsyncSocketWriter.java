package computeythings.garagemonitor.async;

import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import javax.net.ssl.SSLSocket;

import computeythings.garagemonitor.interfaces.SocketResultListener;

public class AsyncSocketWriter extends Thread {
    private static final String TAG = "SOCKET_WRITER";

    public AsyncSocketWriter(final String message, final SSLSocket socket,
                             final SocketResultListener uiListener) {
        super(new Runnable() {
            @Override
            public void run() {
                boolean success;
                try {
                    OutputStream out = socket.getOutputStream();
                    out.write(message.getBytes());
                    out.close(); // Flush and close
                    success = true;
                } catch (IOException | NullPointerException e) {
                    Log.w(TAG, "Error writing message " + message);
                    // If socket is closed, attempt to reopen and resend message.
                    success = false;
                }
                if (uiListener != null)
                    uiListener.onSocketResult(success);
            }
        });
    }
}
