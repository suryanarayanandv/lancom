package com.zoho.lays.lancom.communicator.signalling;

import java.net.Socket;
import java.net.InetSocketAddress;

import java.io.*;

public class SignallingClient {
    public static String pingPeer(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 2000); // 2s timeout

            PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            writer.println("{\"type\":\"PING\"}");
            String response = reader.readLine();

            if (response != null && response.contains("\"status\":\"")) {
                if (response.contains(SignallingConstants.STATUS_AVAILABLE)) return SignallingConstants.STATUS_AVAILABLE;
                if (response.contains(SignallingConstants.STATUS_BUSY)) return SignallingConstants.STATUS_BUSY;
            }

            return SignallingConstants.STATUS_UNKNOWN;

        } catch (IOException e) {
            return SignallingConstants.STATUS_OFFLINE;
        }
    }
}
