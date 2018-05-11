package computeythings.piopener.interfaces;

/**
 * Interface to listen for results when writing to a socket connection
 * <p>
 * Created by bryan on 2/23/18.
 */

public interface SocketResultListener {
    void onSocketResult(boolean success);
    void onSocketData(String data);
}
