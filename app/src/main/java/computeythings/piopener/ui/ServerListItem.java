package computeythings.piopener.ui;

public class ServerListItem {
    private String hostname;
    private String ip;

    public ServerListItem(String hostname, String ip) {
        this.hostname = hostname;
        this.ip = ip;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIp() {
        return ip;
    }
}
