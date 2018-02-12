package computeythings.garagemonitor.settings;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by bryan on 2/11/18.
 */

public class ServerPreferences {
    private static final String TAG = "SERVER_PREFS";
    public static final String SERVER_NAME = "NAME";
    public static final String SERVER_ADDRESS = "ADDRESS";
    public static final String SERVER_PORT = "PORT";
    public static final String SERVER_CERT = "CERT_LOCATION";


    public static String createServerRepresentation(String name, String address, int port,
                                                    int certLocation) {
        JSONObject json = new JSONObject();
        try {
            json.put(SERVER_NAME, name);
            json.put(SERVER_ADDRESS, address);
            json.put(SERVER_PORT, port);
            json.put(SERVER_CERT, certLocation);
        } catch (JSONException e) {
            Log.e(TAG, "Unexpected JSON error");
            e.printStackTrace();
        }
        return json.toString();
    }
}
