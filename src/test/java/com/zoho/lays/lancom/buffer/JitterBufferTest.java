package com.zoho.lays.lancom.buffer;

import com.zoho.lays.lancom.metrics.StreamStats;
import com.zoho.lays.lancom.protocol.AudioPacket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JitterBufferTest {
    private static final int FRAME = 4;

    private StreamStats stats;
    private JitterBuffer buffer;
    private final byte[] out = new byte[FRAME];

    @BeforeEach
    void setUp() {
        stats = new StreamStats();
        buffer = new JitterBuffer(2, 16, FRAME, stats);
    }

    private static AudioPacket packet(int sequence) {
        byte marker = (byte) (sequence & 0x7F | 1);
        return new AudioPacket(1, sequence, 0, 8_000, 1, 16, new byte[] {marker, marker, marker, marker});
    }

    private void assertTakes(int sequence) {
        assertTrue(buffer.take(out), "expected audio for sequence " + sequence);
        assertEquals((byte) (sequence & 0x7F | 1), out[0]);
    }

    private void assertSilence() {
        assertFalse(buffer.take(out));
        assertEquals(0, out[0]);
    }

    @Test
    void outputsSilenceUntilTargetDepthReached() {
        assertSilence();
        buffer.offer(packet(0));
        assertTrue(buffer.isBuffering());
        assertSilence();
        buffer.offer(packet(1));
        assertFalse(buffer.isBuffering());
        assertTakes(0);
        assertTakes(1);
    }

    @Test
    void reordersOutOfOrderPackets() {
        buffer.offer(packet(0));
        buffer.offer(packet(2));
        buffer.offer(packet(1));
        assertTakes(0);
        assertTakes(1);
        assertTakes(2);
    }

    @Test
    void replacesMissingPacketWithSilenceAndCountsLoss() {
        buffer.offer(packet(0));
        buffer.offer(packet(2));
        assertTakes(0);
        assertSilence();
        assertEquals(1, stats.lost());
        assertTakes(2);
    }

    @Test
    void dropsLateAndDuplicatePackets() {
        buffer.offer(packet(5));
        buffer.offer(packet(6));
        assertEquals(JitterBuffer.OfferResult.DUPLICATE, buffer.offer(packet(6)));
        assertTakes(5);
        assertEquals(JitterBuffer.OfferResult.LATE, buffer.offer(packet(5)));
        assertEquals(1, stats.duplicate());
        assertEquals(1, stats.late());
        assertTakes(6);
    }

    @Test
    void underrunTriggersRebuffering() {
        buffer.offer(packet(0));
        buffer.offer(packet(1));
        assertTakes(0);
        assertTakes(1);
        assertSilence();
        assertEquals(1, stats.underruns());
        assertTrue(buffer.isBuffering());

        // After an underrun the buffer re-bases on whatever arrives next, even if packets were skipped.
        buffer.offer(packet(10));
        assertSilence();
        buffer.offer(packet(11));
        assertTakes(10);
        assertEquals(0, stats.lost());
    }

    @Test
    void handlesSequenceWrapAround() {
        // Signed overflow: MAX_VALUE is followed by MIN_VALUE.
        buffer.offer(packet(Integer.MAX_VALUE));
        buffer.offer(packet(Integer.MIN_VALUE));
        assertTakes(Integer.MAX_VALUE);
        assertTakes(Integer.MIN_VALUE);

        // Unsigned wrap: 0xFFFFFFFE, 0xFFFFFFFF, 0, 1.
        JitterBuffer wrapping = new JitterBuffer(2, 16, FRAME, stats);
        wrapping.offer(packet(-2));
        wrapping.offer(packet(-1));
        wrapping.offer(packet(0));
        wrapping.offer(packet(1));
        byte[] frame = new byte[FRAME];
        for (int expected : new int[] {-2, -1, 0, 1}) {
            assertTrue(wrapping.take(frame));
            assertEquals((byte) (expected & 0x7F | 1), frame[0]);
        }
        assertEquals(0, stats.lost());
        assertEquals(0, stats.late());
    }

    @Test
    void dropsOldestPacketsWhenQueueExceedsHighWatermark() {
        for (int i = 0; i < 10; i++) {
            buffer.offer(packet(i));
        }
        // High watermark for target 2 is 4, so 6 of the 10 queued packets are dropped.
        assertTakes(6);
        assertEquals(6, stats.overflowDrops());
        assertEquals(3, buffer.depth());
    }

    @Test
    void resyncsOnLargeSequenceJump() {
        buffer.offer(packet(0));
        buffer.offer(packet(1));
        buffer.offer(packet(1_000));
        assertEquals(1, stats.resyncs());
        assertEquals(1, buffer.depth());
        assertTrue(buffer.isBuffering());
        buffer.offer(packet(1_001));
        assertTakes(1_000);
    }

    @Test
    void resyncsOnLargeBackwardsJump() {
        buffer.offer(packet(1_000));
        buffer.offer(packet(1_001));
        assertTakes(1_000);
        buffer.offer(packet(3)); // e.g. sender restarted with the same stream id
        assertEquals(1, stats.resyncs());
        buffer.offer(packet(4));
        assertTakes(3);
    }

    @Test
    void resetDiscardsQueuedAudio() {
        buffer.offer(packet(0));
        buffer.offer(packet(1));
        buffer.reset();
        assertEquals(0, buffer.depth());
        assertSilence();
    }

    @Test
    void roundsCapacityUpToPowerOfTwo() {
        assertEquals(16, new JitterBuffer(2, 9, FRAME, stats).capacity());
        assertEquals(16, new JitterBuffer(2, 16, FRAME, stats).capacity());
    }

    @Test
    void validatesArguments() {
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(0, 16, FRAME, stats));
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(2, 16, 0, stats));
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(4, 8, FRAME, stats));
        assertThrows(IllegalArgumentException.class, () -> new JitterBuffer(2, (1 << 16) + 1, FRAME, stats));
        assertThrows(NullPointerException.class, () -> new JitterBuffer(2, 16, FRAME, null));
        assertThrows(IllegalArgumentException.class,
                () -> buffer.offer(new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[2])));
        assertThrows(IllegalArgumentException.class, () -> buffer.take(new byte[FRAME + 1]));
    }
}
