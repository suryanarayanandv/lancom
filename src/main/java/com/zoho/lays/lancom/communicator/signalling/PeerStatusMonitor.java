package com.zoho.lays.lancom.communicator.signalling;

import com.zoho.lays.lancom.model.Peer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class PeerStatusMonitor extends Thread {
    private final List<Peer> discoveredPeers;
    private final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
    private boolean running = Boolean.TRUE;

    public PeerStatusMonitor(List<Peer> peers) {
        this.discoveredPeers = peers;
    }

    public void shutdown() {
        running = false;
        System.out.println("PeerStatusMonitor shutting down :: " + new Date().getTime());
    }

    public void getPeersFromStorage() {
        // TODO; get peers
    }

    @Override
    public void run() {
        System.out.println("[Monitor] Started monitoring peers :: " + new Date().getTime());

        while (running) {
            for(Peer peer: discoveredPeers) {
                String peerStatus = SignallingClient.pingPeer(peer.getIp(), peer.getPort());
                statusMap.put(peer.getId(), peerStatus);
                System.out.println("Updated: " + peer.getId() + " = " + peerStatus);
            }

            try {
                Thread.sleep(15000); // Each 15 Second
            } catch (InterruptedException e) {
                System.out.println("PeerStatusMonitor interrupted :: " + e.getMessage());
                running = false; // Stop the thread if interrupted
            }
        }
    }

    public ConcurrentHashMap<String, String> getStatusMap() {
        return statusMap;
    }

    public void addPeer(Peer peer) {
        if (peer != null && !discoveredPeers.contains(peer)) {
            discoveredPeers.add(peer);
            System.out.println("Peer added: " + peer.getId());
        } else {
            System.out.println("Peer already exists or is null: " + (peer != null ? peer.getId() : "null"));
        }
    }
}