package com.zoho.lays.lancom.communicator.signalling;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.*;
import static com.zoho.lays.lancom.communicator.signalling.SignallingConstants.*;

public class SignallingServer extends Thread {
    /*
    Once the peer discovered, the signalling server will be used to establish a connection between the peers, for checking the availability of the peers.
    */
    private ServerSocket serverSocket;
    private final int port;
    private volatile boolean inCall = false;
    private volatile boolean pickUpCall = true;

    public SignallingServer(int port) {
        this.port = port;
        try {
            serverSocket = new ServerSocket(port);
        } catch (Exception e) {
            System.out.println("Signalling Thread exit :: " + this.getId() + " :: Exception listening signal" + e);
        }
    }

    public void setCallStatus(Boolean inCall) {
        if (inCall == null) {
            inCall = false;
        }
        this.inCall = inCall;
    }

    public void setPickUpCall(Boolean pickUpCall) {
        this.pickUpCall = pickUpCall;
        this.inCall = !pickUpCall;
    }



    @Override
    public void run() {
        System.out.println("Signalling server listeing :: Port: " + port);
        while (true) {
            try (Socket clientSocket = serverSocket.accept()) {
                InputStream clientStream = clientSocket.getInputStream();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(clientStream));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true);

                String signal = bufferedReader == null? "": bufferedReader.readLine();

                switch (signal) {
                    case SignallingConstants.PING:
                        String status = inCall? SignallingConstants.STATUS_BUSY: SignallingConstants.STATUS_AVAILABLE;
                        String response = "{\"type\":\"PONG\", \"status\":\"" + status + "\"}";
                        writer.println(response);
                        break;

                    case SignallingConstants.CALL_REQUEST:
                        String response1 = "{\"type\":\"CALL_REQUEST\", " 
                        + "\"user\":\"" + serverSocket.getLocalSocketAddress() + "\"" 
                        + "status\":\"";
                        String status1 = STATUS_ACCEPTED;
                        if (inCall) {
                            status1 = STATUS_BUSY;
                        } else if (pickUpCall) {
                            // TODO: Show incoming call popup ? check is possible
                            // for now
                            status1 = STATUS_ACCEPTED;
                        } else {
                            status1 = STATUS_REJECTED;
                        }
                        
                        response1 += status1 + "\"}";

                        writer.println(response1);
                        break;
                    default:
                        break;
                }
            } catch (IOException e) {
                System.out.println("Error making connection" + e);
            }
        }
    }
}
