package computeythings.garagemonitor.interfaces;

import java.util.Map;

/**
 * Interface to listen for results when a subscribed server document is changed
 * <p>
 * Created by bryan on 2/23/18.
 */
public interface FirebaseDataListener {
    void onDataReceived(Map<String, Object> data);
}
