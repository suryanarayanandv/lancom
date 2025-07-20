package com.zoho.lays.lancom;

import com.zoho.lays.lancom.discovery.DiscoverLanDevices;
import com.zoho.lays.lancom.discovery.DiscoveryConstants;
import com.zoho.lays.lancom.discovery.ServiceDiscovery;
import com.zoho.lays.lancom.discovery.ServiceRegistration;

public class Main {
    public static void main(String[] args) {
        // Test
        try {
            DiscoverLanDevices.start(DiscoveryConstants.SERVICE_NAME);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}