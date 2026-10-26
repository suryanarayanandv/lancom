package com.zoho.lays.lancom.buffer;

import com.zoho.lays.lancom.metrics.StreamStats;
import com.zoho.lays.lancom.protocol.AudioPacket;

import java.util.Arrays;
import java.util.Objects;

/**
 * Reorders packets by sequence number and hands them out at the playback rate.
 *
 * <ul>
 *   <li>Playback starts (or restarts after an underrun) once {@code targetDepth} packets are queued.</li>
 *   <li>A missing packet with later packets queued is counted as lost and replaced with silence.</li>
 *   <li>An empty buffer is an underrun: output silence and re-prebuffer, re-basing on the next arrival.</li>
 *   <li>If the queue grows past a high watermark (sender clock faster than receiver), the oldest packets are
 *       dropped to keep latency bounded.</li>
 *   <li>Large sequence jumps (sender restart, long outage) trigger a resync.</li>
 * </ul>
 *
 * Sequence numbers are compared with wrap-around arithmetic, so streams may run indefinitely.
 * Frame storage is pre-allocated; no allocation happens per packet. All methods are thread-safe.
 */
public final class JitterBuffer {

    public enum OfferResult { ACCEPTED, LATE, DUPLICATE }

    private final int targetDepth;
    private final int capacity;
    private final int mask;
    private final int highWatermark;
    private final int frameBytes;
    private final StreamStats stats;
    private final boolean[] occupied;
    private final byte[][] frames;

    private int count;
    private int nextSequence;
    private boolean hasBase;
    private boolean buffering = true;

    /**
     * @param targetDepth     packets to accumulate before playback starts
     * @param minimumCapacity minimum number of slots; rounded up to a power of two
     * @param frameBytes      exact payload size of every packet
     */
    public JitterBuffer(int targetDepth, int minimumCapacity, int frameBytes, StreamStats stats) {
        if (targetDepth < 1) {
            throw new IllegalArgumentException("targetDepth must be >= 1, was " + targetDepth);
        }
        if (frameBytes <= 0) {
            throw new IllegalArgumentException("frameBytes must be > 0, was " + frameBytes);
        }
        this.highWatermark = highWatermark(targetDepth);
        if (minimumCapacity <= highWatermark) {
            throw new IllegalArgumentException(
                    "capacity must exceed the high watermark " + highWatermark + ", was " + minimumCapacity);
        }
        if (minimumCapacity > (1 << 16)) {
            throw new IllegalArgumentException("capacity too large: " + minimumCapacity);
        }
        this.targetDepth = targetDepth;
        this.capacity = Integer.highestOneBit(minimumCapacity - 1) << 1;
        this.mask = capacity - 1;
        this.frameBytes = frameBytes;
        this.stats = Objects.requireNonNull(stats, "stats");
        this.occupied = new boolean[capacity];
        this.frames = new byte[capacity][frameBytes];
    }

    /** Smallest valid capacity for the given target depth. */
    public static int minimumCapacity(int targetDepth) {
        return highWatermark(targetDepth) + 1;
    }

    private static int highWatermark(int targetDepth) {
        return Math.max(targetDepth * 2, targetDepth + 2);
    }

    public synchronized OfferResult offer(AudioPacket packet) {
        Objects.requireNonNull(packet, "packet");
        if (packet.payloadLength() != frameBytes) {
            throw new IllegalArgumentException(
                    "Expected " + frameBytes + " byte payload, got " + packet.payloadLength());
        }
        int sequence = packet.sequence();
        if (!hasBase) {
            nextSequence = sequence;
            hasBase = true;
        }
        int delta = sequence - nextSequence; // wrap-around safe signed distance
        if (delta < 0) {
            if (delta >= -capacity) {
                stats.incrementLate();
                return OfferResult.LATE;
            }
            resync(sequence);
        } else if (delta >= capacity) {
            resync(sequence);
        }
        int index = sequence & mask;
        if (occupied[index]) {
            stats.incrementDuplicate();
            return OfferResult.DUPLICATE;
        }
        packet.copyPayloadTo(frames[index], 0);
        occupied[index] = true;
        count++;
        if (buffering && count >= targetDepth) {
            buffering = false;
        }
        return OfferResult.ACCEPTED;
    }

    /**
     * Fills {@code destination} with the next frame to play.
     *
     * @return {@code true} if real audio was written, {@code false} if silence was written
     */
    public synchronized boolean take(byte[] destination) {
        Objects.requireNonNull(destination, "destination");
        if (destination.length != frameBytes) {
            throw new IllegalArgumentException("Expected " + frameBytes + " byte buffer, got " + destination.length);
        }
        if (buffering) {
            Arrays.fill(destination, (byte) 0);
            return false;
        }
        while (count > highWatermark) {
            int index = nextSequence & mask;
            if (occupied[index]) {
                occupied[index] = false;
                count--;
                stats.incrementOverflowDrops();
            }
            nextSequence++;
        }
        int index = nextSequence & mask;
        if (occupied[index]) {
            System.arraycopy(frames[index], 0, destination, 0, frameBytes);
            occupied[index] = false;
            count--;
            nextSequence++;
            return true;
        }
        Arrays.fill(destination, (byte) 0);
        if (count == 0) {
            buffering = true;
            hasBase = false;
            stats.incrementUnderruns();
        } else {
            stats.incrementLost();
            nextSequence++;
        }
        return false;
    }

    /** Discards all queued audio and returns to the initial prebuffering state. */
    public synchronized void reset() {
        clearSlots();
        hasBase = false;
        buffering = true;
    }

    public synchronized int depth() {
        return count;
    }

    public synchronized boolean isBuffering() {
        return buffering;
    }

    public int targetDepth() {
        return targetDepth;
    }

    public int capacity() {
        return capacity;
    }

    private void resync(int sequence) {
        clearSlots();
        nextSequence = sequence;
        hasBase = true;
        buffering = true;
        stats.incrementResyncs();
    }

    private void clearSlots() {
        Arrays.fill(occupied, false);
        count = 0;
    }
}
