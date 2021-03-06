package computeythings.piopener.interfaces;

import javax.net.ssl.SSLSocket;

/**
 * Interface to listen for results when creating a socket
 * <p>
 * Created by bryan on 2/23/18.
 */

public interface SocketCreatedListener {
    void onSocketReady(SSLSocket socket, String errorMsg);
}
