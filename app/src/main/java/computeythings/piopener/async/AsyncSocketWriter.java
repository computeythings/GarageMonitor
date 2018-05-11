package computeythings.piopener.async;

import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

import javax.net.ssl.SSLSocket;

import computeythings.piopener.interfaces.SocketResultListener;

public class AsyncSocketWriter extends Thread {
    private static final String TAG = "SOCKET_WRITER";

    public AsyncSocketWriter(final String message, final SSLSocket socket,
                             final SocketResultListener uiListener) {
        super(new Runnable() {
            Handler handler = new Handler();
            boolean success;

            @Override
            public void run() {
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
                if (uiListener != null) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            uiListener.onSocketResult(success);
                        }
                    });
                }
            }
        });
    }
}
