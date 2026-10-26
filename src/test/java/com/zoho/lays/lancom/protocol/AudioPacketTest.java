package com.zoho.lays.lancom.protocol;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioPacketTest {

    @Test
    void copiesPayloadDefensively() {
        byte[] payload = {1, 2, 3, 4};
        AudioPacket packet = new AudioPacket(1, 0, 0, 8_000, 1, 16, payload);
        payload[0] = 99;

        byte[] copy = new byte[4];
        packet.copyPayloadTo(copy, 0);
        assertEquals(1, copy[0]);
    }

    @Test
    void payloadViewIsReadOnly() {
        AudioPacket packet = new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[2]);
        ByteBuffer view = packet.payloadView();
        assertThrows(ReadOnlyBufferException.class, () -> view.put((byte) 1));
    }

    @Test
    void validatesFields() {
        assertThrows(NullPointerException.class, () -> new AudioPacket(1, 0, 0, 8_000, 1, 16, null));
        assertThrows(IllegalArgumentException.class, () -> new AudioPacket(1, 0, -1, 8_000, 1, 16, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new AudioPacket(1, 0, 0, 0, 1, 16, new byte[2]));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioPacket(1, 0, 0, AudioPacket.MAX_SAMPLE_RATE + 1, 1, 16, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new AudioPacket(1, 0, 0, 8_000, 0, 16, new byte[2]));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioPacket(1, 0, 0, 8_000, AudioPacket.MAX_CHANNELS + 1, 16, new byte[18]));
        assertThrows(IllegalArgumentException.class, () -> new AudioPacket(1, 0, 0, 8_000, 1, 8, new byte[2]));
        assertThrows(IllegalArgumentException.class, () -> new AudioPacket(1, 0, 0, 8_000, 2, 16, new byte[2]));
        assertThrows(IllegalArgumentException.class,
                () -> new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[PacketCodec.MAX_PAYLOAD_SIZE + 1]));
    }

    @Test
    void copyPayloadToChecksBounds() {
        AudioPacket packet = new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[4]);
        assertThrows(IndexOutOfBoundsException.class, () -> packet.copyPayloadTo(new byte[3], 0));
        assertThrows(IndexOutOfBoundsException.class, () -> packet.copyPayloadTo(new byte[4], 1));
    }
}
