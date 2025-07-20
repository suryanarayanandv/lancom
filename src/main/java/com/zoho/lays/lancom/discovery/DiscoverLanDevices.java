package com.zoho.lays.lancom.discovery;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import javax.jmdns.JmDNS;

public class DiscoverLanDevices {
    private static JmDNS jmDNS;

    private static int getFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void initialisemDNS(InetAddress localIp) throws Exception {
        jmDNS = JmDNS.create(localIp);
        addShutdownHook();
    }

    public static void start(String serviceName) throws Exception{
        InetAddress localIp = InetAddress.getLocalHost();
        initialisemDNS(localIp);
        ServiceDiscovery.discoverService(jmDNS, serviceName);
        int port = getFreePort();
        ServiceRegistration.registerService(jmDNS, serviceName, port, localIp);
    }

    public static void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jmDNS != null) {
                try {
                    jmDNS.close(); // Close jmDNS when the JVM terminates
                    System.out.println("jmDNS closed successfully.");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }));
    }
    

    public static void stop() throws Exception {
        if (jmDNS == null) {
            System.out.println("JmDNS object not initiated");
            return;
        }
        System.out.println("Closing mDNS service");
        jmDNS.unregisterAllServices();
        jmDNS.close();
    }
}
