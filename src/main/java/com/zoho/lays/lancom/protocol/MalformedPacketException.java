package com.zoho.lays.lancom.protocol;

/**
 * Thrown when a datagram cannot be decoded into a valid {@link AudioPacket}.
 * Datagrams come from the network and are untrusted, so this is an expected, recoverable condition.
 */
public final class MalformedPacketException extends Exception {
    private static final long serialVersionUID = 1L;

    public MalformedPacketException(String message) {
        super(message);
    }

    public MalformedPacketException(String message, Throwable cause) {
        super(message, cause);
    }
}
