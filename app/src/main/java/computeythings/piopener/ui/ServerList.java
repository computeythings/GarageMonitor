package computeythings.piopener.ui;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import computeythings.piopener.R;

public class ServerList extends RecyclerView.Adapter<ServerList.ViewHolder> {
    class ViewHolder extends RecyclerView.ViewHolder {
        TextView hostname;
        TextView ip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            hostname = itemView.findViewById(R.id.server_name);
            ip = itemView.findViewById(R.id.server_ip);
        }
    }

    private ServerListItem[] servers;
    public ServerList(ServerListItem[] servers) {
        this.servers = servers;
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
        ServerListItem server = servers[i];

        holder.hostname.setText(server.getHostname());
        holder.ip.setText(server.getIp());
    }

    @Override
    public int getItemCount() {
        return 0;
    }
}
