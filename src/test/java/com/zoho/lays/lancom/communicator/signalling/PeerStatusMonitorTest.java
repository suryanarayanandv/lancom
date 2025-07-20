package com.zoho.lays.lancom.communicator.signalling;

import java.net.InetAddress;
import java.util.ArrayList;

import org.junit.jupiter.api.Test;
import com.zoho.lays.lancom.model.Peer;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class PeerStatusMonitorTest {
    @Test
    public void testLanForMonitor() throws Exception{
        InetAddress localAddress = InetAddress.getLocalHost();
        ArrayList peerArray = new ArrayList<Peer>() {{
            add(new Peer(localAddress.getHostAddress(), 8443, "TestPeer1"));
        }};

        PeerStatusMonitor monitor = new PeerStatusMonitor(peerArray);
        monitor.join();
        monitor.start();

        Thread.sleep(20000);

        monitor.getStatusMap().forEach((id, status) -> {
            System.out.println("Peer ID: " + id + ", Status: " + status);
        });
        
        monitor.stop();
    }
}
