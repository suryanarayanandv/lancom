package com.zoho.lays.lancom.protocol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * Binary wire format for {@link AudioPacket}s. All multi-byte header fields are big-endian.
 *
 * <pre>
 * offset size field
 *  0     4    magic            0x4C4E434D ("LNCM")
 *  4     1    version          1
 *  5     1    type             1 = audio
 *  6     2    flags            must be 0
 *  8     4    stream id
 * 12     4    sequence         unsigned, wrapping
 * 16     8    sample position
 * 24     4    sample rate (Hz)
 * 28     1    channels
 * 29     1    bits per sample  16
 * 30     2    payload length   unsigned; must equal the remaining datagram bytes
 * 32     n    payload          interleaved signed 16-bit little-endian PCM
 * </pre>
 *
 * Decoding treats input as untrusted: every field is range-checked and declared lengths must match the
 * actual datagram size, so a hostile or corrupt datagram can never cause an over-read or large allocation.
 */
public final class PacketCodec {
    public static final int MAGIC = 0x4C4E434D;
    public static final byte VERSION = 1;
    public static final byte TYPE_AUDIO = 1;
    public static final int HEADER_SIZE = 32;
    /** Largest UDP payload over IPv4. */
    public static final int MAX_DATAGRAM_SIZE = 65_507;
    public static final int MAX_PAYLOAD_SIZE = MAX_DATAGRAM_SIZE - HEADER_SIZE;

    private PacketCodec() {
    }

    public static int encodedSize(AudioPacket packet) {
        return HEADER_SIZE + packet.payloadLength();
    }

    /**
     * Writes {@code packet} into {@code destination} at its current position.
     *
     * @throws IllegalArgumentException if the destination has insufficient space
     */
    public static void encode(AudioPacket packet, ByteBuffer destination) {
        Objects.requireNonNull(packet, "packet");
        Objects.requireNonNull(destination, "destination");
        int size = encodedSize(packet);
        if (destination.remaining() < size) {
            throw new IllegalArgumentException(
                    "Destination too small: need " + size + " bytes, have " + destination.remaining());
        }
        ByteOrder originalOrder = destination.order();
        try {
            destination.order(ByteOrder.BIG_ENDIAN)
                    .putInt(MAGIC)
                    .put(VERSION)
                    .put(TYPE_AUDIO)
                    .putShort((short) 0)
                    .putInt(packet.streamId())
                    .putInt(packet.sequence())
                    .putLong(packet.samplePosition())
                    .putInt(packet.sampleRate())
                    .put((byte) packet.channels())
                    .put((byte) packet.bitsPerSample())
                    .putShort((short) packet.payloadLength())
                    .put(packet.payloadView());
        } finally {
            destination.order(originalOrder);
        }
    }

    /**
     * Decodes one datagram spanning {@code source}'s position to its limit. On success the source is fully
     * consumed; on failure its position is unchanged.
     */
    public static AudioPacket decode(ByteBuffer source) throws MalformedPacketException {
        Objects.requireNonNull(source, "source");
        ByteBuffer in = source.slice().order(ByteOrder.BIG_ENDIAN);
        int length = in.remaining();
        if (length < HEADER_SIZE) {
            throw new MalformedPacketException("Datagram too short: " + length + " bytes");
        }
        if (length > MAX_DATAGRAM_SIZE) {
            throw new MalformedPacketException("Datagram too long: " + length + " bytes");
        }
        int magic = in.getInt();
        if (magic != MAGIC) {
            throw new MalformedPacketException(String.format("Bad magic 0x%08X", magic));
        }
        byte version = in.get();
        if (version != VERSION) {
            throw new MalformedPacketException("Unsupported protocol version " + Byte.toUnsignedInt(version));
        }
        byte type = in.get();
        if (type != TYPE_AUDIO) {
            throw new MalformedPacketException("Unsupported packet type " + Byte.toUnsignedInt(type));
        }
        short flags = in.getShort();
        if (flags != 0) {
            throw new MalformedPacketException(String.format("Unsupported flags 0x%04X", flags));
        }
        int streamId = in.getInt();
        int sequence = in.getInt();
        long samplePosition = in.getLong();
        int sampleRate = in.getInt();
        int channels = Byte.toUnsignedInt(in.get());
        int bitsPerSample = Byte.toUnsignedInt(in.get());
        int payloadLength = Short.toUnsignedInt(in.getShort());
        if (payloadLength != in.remaining()) {
            throw new MalformedPacketException("Payload length mismatch: header declares " + payloadLength
                    + " bytes, datagram carries " + in.remaining());
        }
        byte[] payload = new byte[payloadLength];
        in.get(payload);
        AudioPacket packet;
        try {
            packet = AudioPacket.wrap(streamId, sequence, samplePosition, sampleRate, channels, bitsPerSample, payload);
        } catch (IllegalArgumentException e) {
            throw new MalformedPacketException(e.getMessage(), e);
        }
        source.position(source.position() + length);
        return packet;
    }
}
