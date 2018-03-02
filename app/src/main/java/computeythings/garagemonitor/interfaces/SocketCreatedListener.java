package computeythings.garagemonitor.interfaces;

import javax.net.ssl.SSLSocket;

/**
 * Interface to listen for results when creating a socket
 *
 * Created by bryan on 2/23/18.
 */

public interface SocketCreatedListener {
    void onSocketReady(SSLSocket socket, String errorMsg);
}
