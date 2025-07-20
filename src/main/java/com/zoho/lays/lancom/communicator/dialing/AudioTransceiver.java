package com.zoho.lays.lancom.communicator.dialing;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

public class AudioTransceiver {
    private static final int AUDIO_PORT = 50005;
    private static SourceDataLine speakerDataLine;
    private static TargetDataLine micDataLine;
    private static DatagramSocket sourceSocket;
    private static DatagramSocket targetSocket;
    private static volatile boolean callActive = false;

    
    private static void sendAudio(AudioFormat format, InetAddress targetAddress) throws Exception {
        micDataLine = AudioSystem.getTargetDataLine(format);
        micDataLine.open(format);
        micDataLine.start();
        byte[] buffer = new byte[1024];

        // UDP socket for sharing audio
        sourceSocket = new DatagramSocket();

        while (callActive) {
            int bytesRead = micDataLine.read(buffer, 0, buffer.length);
            DatagramPacket packet = new DatagramPacket(buffer, bytesRead, targetAddress, AUDIO_PORT);
            sourceSocket.send(packet);
        }
    }

    private static void receiveAudio(AudioFormat format) throws Exception {
        speakerDataLine = AudioSystem.getSourceDataLine(format);
        speakerDataLine.open(format);
        speakerDataLine.start();

        byte[] buffer = new byte[1024];

        targetSocket = new DatagramSocket(AUDIO_PORT);
        
        while (callActive) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            targetSocket.receive(packet);
            speakerDataLine.write(buffer, 0, packet.getLength());
        }
    }

    public static void connectPeers(String targetIP) throws Exception{
        AudioFormat format = new AudioFormat(44100.0f, 16, 2, true, true);
        InetAddress targetAddress = InetAddress.getByName(targetIP);
        callActive = true;
        // Start sending audio in a separate thread
        new Thread(() -> {
            try {
                sendAudio(format, targetAddress);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // Start receiving audio
        receiveAudio(format);
    }

    public static void disconnectPeers() {
        System.out.println("Disconnecting peers...");
        callActive = false;
        if (micDataLine != null) {
            micDataLine.stop();
            micDataLine.close();
        }
        if (speakerDataLine != null) {
            speakerDataLine.stop();
            speakerDataLine.close();
        }
        if (sourceSocket != null && !sourceSocket.isClosed()) {
            sourceSocket.close();
        }
        if (targetSocket != null && !targetSocket.isClosed()) {
            targetSocket.close();
        }
        System.out.println("Disconnected.");
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java AudioTransceiver <target IP>");
            return;
        }
        
        String targetIP = args[0];
        try {
            connectPeers(targetIP);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
