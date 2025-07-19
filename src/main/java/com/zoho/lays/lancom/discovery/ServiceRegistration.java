package com.zoho.lays.lancom.discovery;

import java.net.InetAddress;
import javax.jmdns.ServiceInfo;
import javax.jmdns.JmDNS;

public class ServiceRegistration {
    public static void registerService() {
        try {
            // JmDNS Serice
            JmDNS jmDNS = JmDNS.create(InetAddress.getLocalHost());
            ServiceInfo serviceInfo = ServiceInfo.create("_http._tcp.local", "example", 1234, "path=index.html");
    
            jmDNS.registerService(serviceInfo);
    
            Thread.sleep(50000);

            jmDNS.unregisterAllServices();
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
