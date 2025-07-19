package com.zoho.lays.lancom;

import com.zoho.lays.lancom.discovery.ServiceDiscovery;
import com.zoho.lays.lancom.discovery.ServiceRegistration;

public class Main {
    public static void main(String[] args) {
        // Test
        if (args.length == 0) {
            ServiceRegistration.registerService();
        }
        else {
            ServiceDiscovery.discoverService();
        }
    } 
}