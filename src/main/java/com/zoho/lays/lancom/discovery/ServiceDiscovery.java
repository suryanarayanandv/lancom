package com.zoho.lays.lancom.discovery;

import java.net.InetAddress;
import java.net.UnknownHostException;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceListener;
import javax.jmdns.ServiceInfo;

public class ServiceDiscovery {
    private static JmDNS jmDNS;

    private static class LanServiceListener implements ServiceListener {
        @Override
        public void serviceAdded(ServiceEvent event) {
            if (jmDNS == null) {
                jmDNS.requestServiceInfo(event.getType(), event.getName(), true);
            }
            System.out.println("Service added: " + event.getInfo());
        }

        @Override
        public void serviceRemoved(ServiceEvent event) {
            System.out.println("Service removed: " + event.getInfo());
        }

        @Override
        public void serviceResolved(ServiceEvent event) {
            ServiceInfo info = event.getInfo();
            String peerIP = info.getInetAddresses()[0].getHostAddress();
            int peerPort = info.getPort();
            String hostName = info.getAddress().getHostName();

            System.out.println("Discovered peer:");
            System.out.println("  HostName: " + hostName);
            System.out.println("  IP: " + peerIP);
            System.out.println("  Port: " + peerPort);
        }
    }

    public static void discoverService(JmDNS jmDNS, String serviceName) throws Exception {
        try {
            System.out.println("Discovering Service : " + serviceName);
            jmDNS.addServiceListener(DiscoveryConstants.SERVICE_TYPE, new LanServiceListener());
        } catch (Exception e) {
            throw new Exception(DiscoveryConstants.ERROR_SERVICE_DISCOVERY + e.getMessage(), e);
        }
    }
}