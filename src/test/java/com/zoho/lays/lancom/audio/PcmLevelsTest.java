package com.zoho.lays.lancom.audio;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static com.zoho.lays.lancom.audio.PcmChannelConverterTest.pcm;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PcmLevelsTest {

    @Test
    void peakOfSilenceIsZero() {
        assertEquals(0, PcmLevels.peak(new byte[480], 0, 480));
        assertEquals(0, PcmLevels.peak(new byte[0], 0, 0));
    }

    @Test
    void peakIsMaximumMagnitude() {
        assertEquals(500, PcmLevels.peak(pcm(10, -500, 499), 0, 6));
        assertEquals(32_768, PcmLevels.peak(pcm(Short.MIN_VALUE), 0, 2));
        assertEquals(32_767, PcmLevels.peak(pcm(Short.MAX_VALUE), 0, 2));
    }

    @Test
    void peakHonoursRangeAndIgnoresTrailingOddByte() {
        byte[] data = pcm(9_000, 7, 8);
        assertEquals(8, PcmLevels.peak(data, 2, 4));
        assertEquals(9_000, PcmLevels.peak(data, 0, 3));
        assertThrows(IndexOutOfBoundsException.class, () -> PcmLevels.peak(data, 4, 4));
    }

    @Test
    void peakOfByteBufferDoesNotMovePosition() {
        ByteBuffer buffer = ByteBuffer.wrap(pcm(1, -2_000, 3));
        buffer.position(2);
        assertEquals(2_000, PcmLevels.peak(buffer));
        assertEquals(2, buffer.position());
    }

    @Test
    void convertsToDbfs() {
        assertEquals(0.0, PcmLevels.toDbfs(32_768), 1e-9);
        assertEquals(-6.02, PcmLevels.toDbfs(16_384), 0.01);
        assertEquals(Double.NEGATIVE_INFINITY, PcmLevels.toDbfs(0));
        assertEquals("silent", PcmLevels.formatDbfs(0));
        assertEquals("-6.0 dBFS", PcmLevels.formatDbfs(16_384));
        assertThrows(IllegalArgumentException.class, () -> PcmLevels.toDbfs(-1));
        assertThrows(IllegalArgumentException.class, () -> PcmLevels.toDbfs(32_769));
    }
}
