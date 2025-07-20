package com.zoho.lays.lancom.communicator.dialing;

import com.zoho.lays.lancom.communicator.signalling.SignallingClient;
import com.zoho.lays.lancom.communicator.signalling.SignallingConstants;
import com.zoho.lays.lancom.model.Peer;

public class ConnectPeers {
    public static void connectPeer(Peer peer) {
        String status = SignallingClient.requestCallConnection(peer.getIp(), peer.getPort());
        if (status == SignallingConstants.STATUS_ACCEPTED) {
            // Ready to connect via 
            
        } else if (status == SignallingConstants.STATUS_REJECTED) {
            System.out.println("User rejected the call");
            // TODO: handle UI
            try {
                AudioTransceiver.connectPeers(peer.getIp());
            } catch (Exception e) {
                System.out.println("Error connecting to peer: " + e.getMessage());

                // TODO: handle UI
            }
        }
    }

    public static void disconnectPeers() {
        AudioTransceiver.disconnectPeers();
        // TODO: SignallingClient.requestCallDisconnection(); to update status?
    }
}
