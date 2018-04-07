package computeythings.garagemonitor.interfaces;

/**
 * Interface to listen for results when a subscribed server document is changed
 * <p>
 * Created by bryan on 2/23/18.
 */
public interface FirestoreUIListener {
    void onDataReceived(String data);
}
