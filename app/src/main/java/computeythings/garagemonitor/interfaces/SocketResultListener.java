package computeythings.garagemonitor.interfaces;

/**
 * Interface to listen for results when writing to a socket connection
 *
 * Created by bryan on 2/23/18.
 */

public interface SocketResultListener {
    void onSocketResult(Boolean success);
}