package com.zoho.lays.lancom.protocol;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Immutable audio datagram: one block of interleaved, signed 16-bit little-endian PCM samples plus the
 * metadata the receiver needs to validate, reorder and schedule it.
 */
public final class AudioPacket {
    public static final int SUPPORTED_BITS_PER_SAMPLE = 16;
    public static final int MAX_CHANNELS = 8;
    public static final int MAX_SAMPLE_RATE = 192_000;

    private final int streamId;
    private final int sequence;
    private final long samplePosition;
    private final int sampleRate;
    private final int channels;
    private final int bitsPerSample;
    private final byte[] payload;

    /**
     * Creates a packet. The payload is defensively copied.
     *
     * @param streamId       random identifier chosen by the sender for the lifetime of one stream
     * @param sequence       wrapping packet counter, incremented by one per packet
     * @param samplePosition index of the first sample frame in this packet since the stream started
     * @throws IllegalArgumentException if any field is out of range
     */
    public AudioPacket(int streamId, int sequence, long samplePosition, int sampleRate, int channels,
                       int bitsPerSample, byte[] payload) {
        this(streamId, sequence, samplePosition, sampleRate, channels, bitsPerSample,
                Objects.requireNonNull(payload, "payload").clone(), true);
    }

    private AudioPacket(int streamId, int sequence, long samplePosition, int sampleRate, int channels,
                        int bitsPerSample, byte[] payload, boolean owned) {
        if (samplePosition < 0) {
            throw new IllegalArgumentException("samplePosition must be >= 0, was " + samplePosition);
        }
        if (sampleRate <= 0 || sampleRate > MAX_SAMPLE_RATE) {
            throw new IllegalArgumentException("sampleRate out of range: " + sampleRate);
        }
        if (channels < 1 || channels > MAX_CHANNELS) {
            throw new IllegalArgumentException("channels out of range: " + channels);
        }
        if (bitsPerSample != SUPPORTED_BITS_PER_SAMPLE) {
            throw new IllegalArgumentException("Unsupported bitsPerSample: " + bitsPerSample);
        }
        if (payload.length > PacketCodec.MAX_PAYLOAD_SIZE) {
            throw new IllegalArgumentException("Payload too large: " + payload.length + " bytes");
        }
        int frameSize = channels * (bitsPerSample / 8);
        if (payload.length % frameSize != 0) {
            throw new IllegalArgumentException(
                    "Payload length " + payload.length + " is not a multiple of the frame size " + frameSize);
        }
        this.streamId = streamId;
        this.sequence = sequence;
        this.samplePosition = samplePosition;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.payload = payload;
    }

    /** Takes ownership of {@code payload} without copying. Only for use by the codec. */
    static AudioPacket wrap(int streamId, int sequence, long samplePosition, int sampleRate, int channels,
                            int bitsPerSample, byte[] payload) {
        return new AudioPacket(streamId, sequence, samplePosition, sampleRate, channels, bitsPerSample, payload, true);
    }

    public int streamId() {
        return streamId;
    }

    public int sequence() {
        return sequence;
    }

    public long samplePosition() {
        return samplePosition;
    }

    public int sampleRate() {
        return sampleRate;
    }

    public int channels() {
        return channels;
    }

    public int bitsPerSample() {
        return bitsPerSample;
    }

    public int payloadLength() {
        return payload.length;
    }

    /** Returns a read-only view of the payload; no copy is made. */
    public ByteBuffer payloadView() {
        return ByteBuffer.wrap(payload).asReadOnlyBuffer();
    }

    /** Copies the payload into {@code destination} starting at {@code offset}. */
    public void copyPayloadTo(byte[] destination, int offset) {
        Objects.checkFromIndexSize(offset, payload.length, destination.length);
        System.arraycopy(payload, 0, destination, offset, payload.length);
    }

    @Override
    public String toString() {
        return String.format("AudioPacket[stream=%08x, seq=%d, pos=%d, %d Hz, %d ch, %d bytes]",
                streamId, Integer.toUnsignedLong(sequence), samplePosition, sampleRate, channels, payload.length);
    }
}
