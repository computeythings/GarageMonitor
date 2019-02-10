package computeythings.piopener.ui;

import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import computeythings.piopener.R;
import computeythings.piopener.async.AsyncServerDiscover;

public class AddServerFragment extends Fragment {
    private static final String TAG = "AS_FRAGMENT";

    private InetAddress getBroadcastAddress() throws IOException {
        WifiManager wifi = (WifiManager) Objects.requireNonNull(getActivity())
                .getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        DhcpInfo dhcp;

        if (wifi != null) {
            dhcp = wifi.getDhcpInfo();
        } else {
            Log.e(TAG, "Could not connect to the network.");
            return null;
        }


        int broadcast = (dhcp.ipAddress & dhcp.netmask) | ~dhcp.netmask;
        byte[] quads = new byte[4];
        for (int k = 0; k < 4; k++)
            quads[k] = (byte) (broadcast >> (k * 8));
        return InetAddress.getByAddress(quads);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_addserver, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        RecyclerView serverList = view.findViewById(R.id.discover_servers_list);
        ServerListAdapter adapter = new ServerListAdapter();
        serverList.setAdapter(adapter);

        try {
            new AsyncServerDiscover(adapter).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                    getBroadcastAddress());
        } catch (IOException e) {
            Log.e(TAG, "Could not get broadcast address");
            e.printStackTrace();
        }


    }
}
