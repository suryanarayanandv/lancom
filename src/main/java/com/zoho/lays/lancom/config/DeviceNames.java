package com.zoho.lays.lancom.config;

import java.util.Optional;

/** Validation for user-supplied audio device names. */
final class DeviceNames {
    static final int MAX_LENGTH = 256;

    private DeviceNames() {
    }

    static Optional<String> normalize(String deviceName) {
        if (deviceName == null) {
            return Optional.empty();
        }
        String trimmed = deviceName.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Device name longer than " + MAX_LENGTH + " characters");
        }
        return Optional.of(trimmed);
    }
}
