package computeythings.piopener.ui;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;

import computeythings.piopener.R;

public class ServerListAdapter extends RecyclerView.Adapter<ServerListAdapter.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        TextView hostname;
        TextView ip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            hostname = itemView.findViewById(R.id.server_name);
            ip = itemView.findViewById(R.id.server_ip);
        }
    }

    private ArrayList<ServerListItem> servers;
    ServerListAdapter() {
        this.servers = new ArrayList<>();
    }

    public void addServer(ServerListItem server) {
        servers.add(server);
        notifyItemInserted(servers.size()-1);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int i) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.serverlist_item,
                parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int i) {
        ServerListItem server = servers.get(i);

        holder.hostname.setText(server.getHostname());
        holder.ip.setText(server.getIp());
    }

    @Override
    public int getItemCount() {
        return 0;
    }
}
