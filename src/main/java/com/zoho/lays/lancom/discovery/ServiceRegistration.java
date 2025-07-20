package com.zoho.lays.lancom.discovery;

import java.net.InetAddress;
import javax.jmdns.ServiceInfo;
import javax.jmdns.JmDNS;

public class ServiceRegistration {
    public static void registerService(JmDNS jmDNS, String serviceName, int port, InetAddress localIP) throws Exception {
        try {
            // JmDNS Serice
            ServiceInfo serviceInfo = ServiceInfo.create(
                DiscoveryConstants.SERVICE_TYPE, 
                serviceName + "-" + localIP.getHostAddress(), 
                port, "path=index.html");
    
            jmDNS.registerService(serviceInfo);

            System.out.println("Service Registered on port : " + port);
        } catch (Exception e) {
            throw new Exception(DiscoveryConstants.ERROR_SERVICE_REGISTRATION + e.getMessage(), e);
        }
    }
}
