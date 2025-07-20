package com.zoho.lays.lancom.model;

public class Peer {
    private final String ip;
    private final int port;
    private String name;

    public Peer(String ip, int port, String name) {
        this.ip = ip;
        this.port = port;
        this.name = name;
    }

    public String getId() {
        return this.ip + ":" + port;
    }

    // Getter
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }
}
