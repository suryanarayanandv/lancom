package com.zoho.lays.lancom.audio;

import java.io.IOException;

/** An audio device could not be found, opened or used. */
public final class AudioDeviceException extends IOException {
    private static final long serialVersionUID = 1L;

    public AudioDeviceException(String message) {
        super(message);
    }

    public AudioDeviceException(String message, Throwable cause) {
        super(message, cause);
    }
}
