package com.zoho.lays.lancom.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketCodecTest {

    private static AudioPacket samplePacket(int sequence) {
        byte[] payload = new byte[160];
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 7 + sequence);
        }
        return new AudioPacket(0xCAFEBABE, sequence, 123_456_789L, 48_000, 2, 16, payload);
    }

    private static ByteBuffer encode(AudioPacket packet) {
        ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.encodedSize(packet));
        PacketCodec.encode(packet, buffer);
        buffer.flip();
        return buffer;
    }

    private static byte[] payloadOf(AudioPacket packet) {
        byte[] bytes = new byte[packet.payloadLength()];
        packet.copyPayloadTo(bytes, 0);
        return bytes;
    }

    @Test
    void roundTripPreservesAllFields() throws Exception {
        AudioPacket original = samplePacket(-1); // max unsigned sequence
        ByteBuffer wire = encode(original);
        assertEquals(PacketCodec.HEADER_SIZE + 160, wire.remaining());

        AudioPacket decoded = PacketCodec.decode(wire);

        assertEquals(original.streamId(), decoded.streamId());
        assertEquals(original.sequence(), decoded.sequence());
        assertEquals(original.samplePosition(), decoded.samplePosition());
        assertEquals(original.sampleRate(), decoded.sampleRate());
        assertEquals(original.channels(), decoded.channels());
        assertEquals(original.bitsPerSample(), decoded.bitsPerSample());
        assertArrayEquals(payloadOf(original), payloadOf(decoded));
        assertEquals(0, wire.remaining(), "decode consumes the datagram");
    }

    @Test
    void roundTripWithEmptyPayload() throws Exception {
        AudioPacket original = new AudioPacket(1, 2, 0, 8_000, 1, 16, new byte[0]);
        assertEquals(0, PacketCodec.decode(encode(original)).payloadLength());
    }

    @Test
    void encodeRejectsTooSmallDestination() {
        AudioPacket packet = samplePacket(0);
        ByteBuffer tooSmall = ByteBuffer.allocate(PacketCodec.encodedSize(packet) - 1);
        assertThrows(IllegalArgumentException.class, () -> PacketCodec.encode(packet, tooSmall));
        assertEquals(0, tooSmall.position());
    }

    @Test
    void rejectsShortDatagram() {
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decode(ByteBuffer.allocate(PacketCodec.HEADER_SIZE - 1)));
    }

    @Test
    void rejectsBadMagic() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(0, (byte) 0);
        assertMalformed(wire, "magic");
    }

    @Test
    void rejectsUnknownVersion() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(4, (byte) 2);
        assertMalformed(wire, "version");
    }

    @Test
    void rejectsUnknownType() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(5, (byte) 9);
        assertMalformed(wire, "type");
    }

    @Test
    void rejectsNonZeroFlags() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.putShort(6, (short) 1);
        assertMalformed(wire, "flags");
    }

    @Test
    void rejectsDeclaredLengthLongerThanDatagram() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.putShort(30, (short) 0xFFFF);
        assertMalformed(wire, "length");
    }

    @Test
    void rejectsTruncatedPayload() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.limit(wire.limit() - 1);
        assertMalformed(wire, "length");
    }

    @Test
    void rejectsTrailingBytes() {
        AudioPacket packet = samplePacket(0);
        ByteBuffer wire = ByteBuffer.allocate(PacketCodec.encodedSize(packet) + 4);
        PacketCodec.encode(packet, wire);
        wire.position(0);
        assertMalformed(wire, "length");
    }

    @Test
    void rejectsInvalidChannels() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(28, (byte) 0);
        assertMalformed(wire, "channels");
    }

    @Test
    void rejectsUnsupportedBitDepth() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(29, (byte) 24);
        assertMalformed(wire, "bitsPerSample");
    }

    @Test
    void rejectsInvalidSampleRate() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.putInt(24, -1);
        assertMalformed(wire, "sampleRate");
    }

    @Test
    void rejectsNegativeSamplePosition() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.putLong(16, -5);
        assertMalformed(wire, "samplePosition");
    }

    @Test
    void rejectsPayloadNotMultipleOfFrameSize() {
        AudioPacket packet = new AudioPacket(1, 1, 0, 8_000, 1, 16, new byte[4]);
        ByteBuffer wire = encode(packet);
        wire.put(28, (byte) 3); // 3 channels -> 6-byte frames, payload is 4 bytes
        assertMalformed(wire, "frame size");
    }

    @Test
    void failedDecodeLeavesSourcePositionUntouched() {
        ByteBuffer wire = encode(samplePacket(0));
        wire.put(0, (byte) 0);
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(wire));
        assertEquals(0, wire.position());
    }

    @Test
    void randomGarbageNeverCausesUnexpectedExceptions() {
        Random random = new Random(42);
        byte[] valid = encode(samplePacket(3)).array();
        for (int i = 0; i < 20_000; i++) {
            byte[] data;
            if (i % 2 == 0) {
                data = new byte[random.nextInt(300)];
                random.nextBytes(data);
            } else {
                data = valid.clone();
                data[random.nextInt(data.length)] = (byte) random.nextInt();
            }
            try {
                PacketCodec.decode(ByteBuffer.wrap(data));
            } catch (MalformedPacketException expected) {
                // fine
            }
        }
    }

    private static void assertMalformed(ByteBuffer wire, String messageFragment) {
        MalformedPacketException e = assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(wire));
        assertTrue(e.getMessage().toLowerCase().contains(messageFragment.toLowerCase()),
                () -> "Expected message containing '" + messageFragment + "' but was: " + e.getMessage());
    }
}
