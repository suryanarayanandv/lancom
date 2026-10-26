package com.zoho.lays.lancom.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PcmChannelConverterTest {

    @Test
    void monoToStereoDuplicatesEachSample() {
        byte[] mono = pcm(1, -1, Short.MAX_VALUE, Short.MIN_VALUE);
        byte[] stereo = new byte[16];
        assertEquals(16, PcmChannelConverter.convert(mono, 0, 4, 1, stereo, 0, 2));
        assertArrayEquals(pcm(1, 1, -1, -1, Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE),
                stereo);
    }

    @Test
    void stereoToMonoAveragesWithoutOverflow() {
        byte[] stereo = pcm(100, 200, Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE,
                Short.MAX_VALUE, Short.MIN_VALUE, -3, 0);
        byte[] mono = new byte[10];
        assertEquals(10, PcmChannelConverter.convert(stereo, 0, 5, 2, mono, 0, 1));
        // (32767 + -32768) >> 1 = -1 and (-3 + 0) >> 1 = -2: arithmetic shift floors.
        assertArrayEquals(pcm(150, Short.MAX_VALUE, Short.MIN_VALUE, -1, -2), mono);
    }

    @Test
    void sameChannelCountCopies() {
        byte[] source = pcm(5, 6, 7, 8);
        byte[] destination = new byte[source.length];
        PcmChannelConverter.convert(source, 0, 2, 2, destination, 0, 2);
        assertArrayEquals(source, destination);
    }

    @Test
    void honoursOffsets() {
        byte[] source = new byte[2 + 4];
        System.arraycopy(pcm(42, -42), 0, source, 2, 4);
        byte[] destination = new byte[3 + 8];
        PcmChannelConverter.convert(source, 2, 2, 1, destination, 3, 2);
        byte[] converted = new byte[8];
        System.arraycopy(destination, 3, converted, 0, 8);
        assertArrayEquals(pcm(42, 42, -42, -42), converted);
        assertEquals(0, destination[0]);
    }

    @Test
    void zeroFramesIsNoOp() {
        assertEquals(0, PcmChannelConverter.convert(new byte[0], 0, 0, 1, new byte[0], 0, 2));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> PcmChannelConverter.convert(new byte[4], 0, 1, 3, new byte[4], 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> PcmChannelConverter.convert(new byte[4], 0, -1, 1, new byte[4], 0, 2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> PcmChannelConverter.convert(new byte[4], 0, 2, 1, new byte[4], 0, 2));
        assertThrows(IndexOutOfBoundsException.class,
                () -> PcmChannelConverter.convert(new byte[4], 2, 2, 1, new byte[8], 0, 2));
        assertThrows(NullPointerException.class,
                () -> PcmChannelConverter.convert(null, 0, 0, 1, new byte[0], 0, 2));
    }

    static byte[] pcm(int... samples) {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int sample : samples) {
            buffer.putShort((short) sample);
        }
        return buffer.array();
    }
}
