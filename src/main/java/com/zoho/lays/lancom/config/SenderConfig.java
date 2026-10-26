package com.zoho.lays.lancom.config;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

/** Immutable, validated configuration for the sending node. */
public final class SenderConfig {
    private final InetSocketAddress target;
    private final AudioSettings audio;
    private final Optional<String> captureDevice;

    private SenderConfig(Builder builder) {
        if (builder.target == null) {
            throw new IllegalArgumentException("Target address is required");
        }
        if (builder.target.isUnresolved()) {
            throw new IllegalArgumentException("Target host could not be resolved: " + builder.target.getHostString());
        }
        if (builder.target.getPort() == 0) {
            throw new IllegalArgumentException("Target port must be between 1 and 65535");
        }
        this.target = builder.target;
        this.audio = builder.audio;
        this.captureDevice = DeviceNames.normalize(builder.captureDevice);
    }

    public static Builder builder() {
        return new Builder();
    }

    public InetSocketAddress target() {
        return target;
    }

    public AudioSettings audio() {
        return audio;
    }

    /** Substring of the capture device name; empty means the system default input. */
    public Optional<String> captureDevice() {
        return captureDevice;
    }

    public static final class Builder {
        private InetSocketAddress target;
        private AudioSettings audio = AudioSettings.defaults();
        private String captureDevice;

        private Builder() {
        }

        public Builder target(InetSocketAddress target) {
            this.target = target;
            return this;
        }

        public Builder audio(AudioSettings audio) {
            this.audio = Objects.requireNonNull(audio, "audio");
            return this;
        }

        public Builder captureDevice(String captureDevice) {
            this.captureDevice = captureDevice;
            return this;
        }

        public SenderConfig build() {
            return new SenderConfig(this);
        }
    }
}
