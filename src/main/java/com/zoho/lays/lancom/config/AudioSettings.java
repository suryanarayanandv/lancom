package com.zoho.lays.lancom.config;

import com.zoho.lays.lancom.protocol.AudioPacket;

import javax.sound.sampled.AudioFormat;
import java.util.List;
import java.util.Objects;

/**
 * Audio format shared by sender and receiver: signed 16-bit little-endian PCM, interleaved channels,
 * split into fixed-duration packets. Both ends must use identical settings.
 */
public final class AudioSettings {
    public static final int BITS_PER_SAMPLE = 16;
    public static final int BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8;
    public static final List<Integer> SUPPORTED_SAMPLE_RATES = List.of(8_000, 16_000, 24_000, 32_000, 44_100, 48_000);
    public static final List<Integer> SUPPORTED_FRAME_DURATIONS_MS = List.of(5, 10, 20, 40);
    public static final int DEFAULT_SAMPLE_RATE = 48_000;
    /** Mono: microphones are mono, and it halves bandwidth. Devices that need stereo are converted. */
    public static final int DEFAULT_CHANNELS = 1;
    public static final int DEFAULT_FRAME_DURATION_MS = 10;

    private final int sampleRate;
    private final int channels;
    private final int frameDurationMs;

    public AudioSettings(int sampleRate, int channels, int frameDurationMs) {
        if (!SUPPORTED_SAMPLE_RATES.contains(sampleRate)) {
            throw new IllegalArgumentException(
                    "Unsupported sample rate " + sampleRate + " Hz; supported: " + SUPPORTED_SAMPLE_RATES);
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Channels must be 1 or 2, was " + channels);
        }
        if (!SUPPORTED_FRAME_DURATIONS_MS.contains(frameDurationMs)) {
            throw new IllegalArgumentException(
                    "Unsupported frame duration " + frameDurationMs + " ms; supported: " + SUPPORTED_FRAME_DURATIONS_MS);
        }
        if ((long) sampleRate * frameDurationMs % 1000 != 0) {
            throw new IllegalArgumentException("Frame duration " + frameDurationMs + " ms is not a whole number of "
                    + "samples at " + sampleRate + " Hz");
        }
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.frameDurationMs = frameDurationMs;
    }

    public static AudioSettings defaults() {
        return new AudioSettings(DEFAULT_SAMPLE_RATE, DEFAULT_CHANNELS, DEFAULT_FRAME_DURATION_MS);
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    public int frameDurationMs() {
        return frameDurationMs;
    }

    /** Sample frames (one sample per channel) per packet. */
    public int samplesPerPacket() {
        return sampleRate * frameDurationMs / 1000;
    }

    public int bytesPerSampleFrame() {
        return channels * BYTES_PER_SAMPLE;
    }

    public int payloadBytes() {
        return samplesPerPacket() * bytesPerSampleFrame();
    }

    public AudioFormat toAudioFormat() {
        return new AudioFormat(sampleRate, BITS_PER_SAMPLE, channels, true, false);
    }

    /** Whether a received packet carries audio in exactly this format. */
    public boolean matches(AudioPacket packet) {
        return packet.sampleRate() == sampleRate
                && packet.channels() == channels
                && packet.bitsPerSample() == BITS_PER_SAMPLE
                && packet.payloadLength() == payloadBytes();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AudioSettings)) {
            return false;
        }
        AudioSettings other = (AudioSettings) o;
        return sampleRate == other.sampleRate && channels == other.channels && frameDurationMs == other.frameDurationMs;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sampleRate, channels, frameDurationMs);
    }

    @Override
    public String toString() {
        return sampleRate + " Hz, " + channels + " ch, " + BITS_PER_SAMPLE + "-bit, " + frameDurationMs + " ms frames";
    }
}
