package com.zoho.lays.lancom.config;

import com.zoho.lays.lancom.buffer.JitterBuffer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, validated configuration for the receiving node. */
public final class ReceiverConfig {
    public static final int DEFAULT_PORT = 50_005;
    public static final int DEFAULT_JITTER_MS = 60;
    public static final int MAX_JITTER_MS = 2_000;
    public static final Duration DEFAULT_STREAM_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration MIN_STREAM_TIMEOUT = Duration.ofMillis(100);
    public static final Duration MAX_STREAM_TIMEOUT = Duration.ofMinutes(1);
    public static final int MAX_ALLOWED_SOURCES = 64;

    private final InetSocketAddress bindAddress;
    private final AudioSettings audio;
    private final Optional<String> playbackDevice;
    private final Set<InetAddress> allowedSources;
    private final int jitterMs;
    private final Duration streamTimeout;

    private ReceiverConfig(Builder builder) {
        if (builder.bindAddress.isUnresolved()) {
            throw new IllegalArgumentException("Bind address could not be resolved: " + builder.bindAddress.getHostString());
        }
        if (builder.allowedSources.size() > MAX_ALLOWED_SOURCES) {
            throw new IllegalArgumentException("At most " + MAX_ALLOWED_SOURCES + " allowed sources are supported");
        }
        int frameMs = builder.audio.frameDurationMs();
        if (builder.jitterMs < frameMs || builder.jitterMs > MAX_JITTER_MS) {
            throw new IllegalArgumentException(
                    "Jitter buffer must be between " + frameMs + " and " + MAX_JITTER_MS + " ms, was " + builder.jitterMs);
        }
        if (builder.streamTimeout.compareTo(MIN_STREAM_TIMEOUT) < 0
                || builder.streamTimeout.compareTo(MAX_STREAM_TIMEOUT) > 0) {
            throw new IllegalArgumentException("Stream timeout must be between " + MIN_STREAM_TIMEOUT.toMillis()
                    + " and " + MAX_STREAM_TIMEOUT.toMillis() + " ms");
        }
        this.bindAddress = builder.bindAddress;
        this.audio = builder.audio;
        this.playbackDevice = DeviceNames.normalize(builder.playbackDevice);
        this.allowedSources = Set.copyOf(builder.allowedSources);
        this.jitterMs = builder.jitterMs;
        this.streamTimeout = builder.streamTimeout;
    }

    public static Builder builder() {
        return new Builder();
    }

    public InetSocketAddress bindAddress() {
        return bindAddress;
    }

    public AudioSettings audio() {
        return audio;
    }

    /** Substring of the playback device name (e.g. Bluetooth headphones); empty means the system default output. */
    public Optional<String> playbackDevice() {
        return playbackDevice;
    }

    /** Source addresses allowed to stream to this receiver; empty means any source. */
    public Set<InetAddress> allowedSources() {
        return allowedSources;
    }

    public boolean isSourceAllowed(InetAddress source) {
        return allowedSources.isEmpty() || allowedSources.contains(source);
    }

    public int jitterMs() {
        return jitterMs;
    }

    /** How long the active stream must be silent before another stream may take over. */
    public Duration streamTimeout() {
        return streamTimeout;
    }

    public int jitterTargetPackets() {
        return Math.max(1, (jitterMs + audio.frameDurationMs() - 1) / audio.frameDurationMs());
    }

    public int jitterCapacityPackets() {
        int target = jitterTargetPackets();
        return Math.max(JitterBuffer.minimumCapacity(target), target * 4 + 8);
    }

    public static final class Builder {
        private InetSocketAddress bindAddress = new InetSocketAddress(DEFAULT_PORT);
        private AudioSettings audio = AudioSettings.defaults();
        private String playbackDevice;
        private Set<InetAddress> allowedSources = Set.of();
        private int jitterMs = DEFAULT_JITTER_MS;
        private Duration streamTimeout = DEFAULT_STREAM_TIMEOUT;

        private Builder() {
        }

        public Builder bindAddress(InetSocketAddress bindAddress) {
            this.bindAddress = Objects.requireNonNull(bindAddress, "bindAddress");
            return this;
        }

        public Builder audio(AudioSettings audio) {
            this.audio = Objects.requireNonNull(audio, "audio");
            return this;
        }

        public Builder playbackDevice(String playbackDevice) {
            this.playbackDevice = playbackDevice;
            return this;
        }

        public Builder allowedSources(Collection<InetAddress> allowedSources) {
            this.allowedSources = Set.copyOf(Objects.requireNonNull(allowedSources, "allowedSources"));
            return this;
        }

        public Builder jitterMs(int jitterMs) {
            this.jitterMs = jitterMs;
            return this;
        }

        public Builder streamTimeout(Duration streamTimeout) {
            this.streamTimeout = Objects.requireNonNull(streamTimeout, "streamTimeout");
            return this;
        }

        public ReceiverConfig build() {
            return new ReceiverConfig(this);
        }
    }
}
