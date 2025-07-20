package com.zoho.lays.lancom.communicator.signalling;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;

public class SignallingServer extends Thread {
    /*
    Once the peer discovered, the signalling server will be used to establish a connection between the peers, for checking the availability of the peers.
    */
    private ServerSocket serverSocket;
    private final int port;
    private volatile boolean inCall = false;

    public SignallingServer(int port) {
        this.port = port;
        try {
            serverSocket = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println("Signalling Thread exit :: " + this.getId() + " :: Exception listening signal" + e);
        }
    }

    @Override
    public void run() {
        System.out.println("Signalling server listeing :: Port: " + port);
        while (true) {
            try (Socket clientSocket = serverSocket.accept()) {
                InputStream clientStream = clientSocket.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientStream));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

                if (bufferedReader != null && bufferedReader.readLine().contains("\"type\":\"PING\"")) {
                    String status = inCall? SignallingConstants.STATUS_BUSY: SignallingConstants.STATUS_AVAILABLE;
                    String response = "{\"type\":\"PONG\", \"status\":\"" + status + "\"}";
                    writer.println(response);
                }
            } catch (IOException e) {
                System.out.println("Error making connection" + e);
            }
        }
    }
}
