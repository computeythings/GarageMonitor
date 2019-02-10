package computeythings.piopener.async;

import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import computeythings.piopener.ui.ServerListAdapter;

public class AsyncServerDiscover extends AsyncTask<InetAddress, String, Boolean> {
    private static final String TAG = "SERVER_DICOVER";
    private static final int SERVER_PORT = 41234;
    private static final int CLIENT_PORT = 41233;
    private static final String SERVER_RESPONSE = "PI_OPENER_SERVER_ACK";
    private static final String CLIENT_QUERY = "ANDROID_CLIENT_PI_OPENER";

    private ServerListAdapter adapter;

    public AsyncServerDiscover(ServerListAdapter adapter) {
        this.adapter = adapter;
    }

    private void updBroadcast(InetAddress broadcastAddr) {
        try {
            DatagramSocket broadcastSocket = new DatagramSocket();
            broadcastSocket.setBroadcast(true);

            byte[] sendData = AsyncServerDiscover.CLIENT_QUERY.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, broadcastAddr,
                    SERVER_PORT);

            broadcastSocket.send(sendPacket);
            broadcastSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to create upd broadcast message.");
            e.printStackTrace();
        }
    }

    @Override
    protected void onProgressUpdate(String... serverIP) {
        //TODO: get server info
        //adapter.addServer();
    }

    @Override
    protected Boolean doInBackground(InetAddress... addr) {
        if (addr[0] == null) {
            Log.e(TAG, "Cannot find broadcast address.");
            return null;
        }

        try {
            DatagramSocket clientSocket = new DatagramSocket(CLIENT_PORT,
                    InetAddress.getByName("0.0.0.0"));
            clientSocket.setBroadcast(true);
            clientSocket.setSoTimeout(10000);

            updBroadcast(addr[0]);

            byte[] recvBuf = new byte[15000];
            DatagramPacket recvPacket;
            String data;
            while(true) {
                recvPacket = new DatagramPacket(recvBuf, recvBuf.length);
                clientSocket.receive(recvPacket);

                String recvAddr = recvPacket.getAddress().getHostAddress();
                Log.i(TAG, "Packet received from: " + recvAddr);
                data = new String(recvPacket.getData()).trim();
                Log.i(TAG, "Packet received; data: " + data);

                if(data.equals(SERVER_RESPONSE)) {
                    Log.i(TAG, "Found server at: " + recvAddr);
                    publishProgress(recvAddr);
                }
            }
        } catch (IOException e) {
            if(e instanceof SocketTimeoutException)
                return true;
            Log.e(TAG, "Failed to create listener on local UDP port");
            e.printStackTrace();
        }

        return false;
    }
}
