package com.zoho.lays.lancom.communicator.signalling;

public class SignallingConstants {
    public static final String PING = "{\"type\":\"PING\"";
    public static final String PONG = "{\"type\":\"PONG\"";
    public static final String CALL_REQUEST = "{\"type\":\"CALL_REQUEST\"";

    public static final String RESPONSE_USER_BUSY = "{\"type\":\"USER_BUSY\", \"message\":\"User is busy\", ";

    public static final String STATUS_AVAILABLE = "available";
    public static final String STATUS_BUSY = "busy";
    public static final String STATUS_TIMEOUT = "timeout";
    public static final String STATUS_UNKNOWN = "unknown";
    public static final String STATUS_OFFLINE = "offline";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_ACCEPTED = "accepted";
}
