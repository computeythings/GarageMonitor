package computeythings.garagemonitor.interfaces;

import javax.net.ssl.SSLSocket;

/**
 * Created by bryan on 2/23/18.
 */

public interface SocketCreatedListener {
    void onSocketReady(SSLSocket socket);
}
