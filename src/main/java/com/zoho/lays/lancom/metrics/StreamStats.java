package com.zoho.lays.lancom.metrics;

import com.zoho.lays.lancom.audio.PcmLevels;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe counters describing the health of an audio stream, plus peak audio levels per reporting
 * period. Packet counters alone cannot distinguish real audio from a stream of silent packets.
 */
public final class StreamStats {
    private static final int NOT_MEASURED = -1;

    private final AtomicInteger capturePeak = new AtomicInteger(NOT_MEASURED);
    private final AtomicInteger playbackPeak = new AtomicInteger(NOT_MEASURED);
    private final AtomicLong sent = new AtomicLong();
    private final AtomicLong sendErrors = new AtomicLong();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong malformed = new AtomicLong();
    private final AtomicLong formatMismatch = new AtomicLong();
    private final AtomicLong late = new AtomicLong();
    private final AtomicLong duplicate = new AtomicLong();
    private final AtomicLong lost = new AtomicLong();
    private final AtomicLong underruns = new AtomicLong();
    private final AtomicLong resyncs = new AtomicLong();
    private final AtomicLong overflowDrops = new AtomicLong();

    public long incrementSent() {
        return sent.incrementAndGet();
    }

    public long incrementSendErrors() {
        return sendErrors.incrementAndGet();
    }

    public long incrementReceived() {
        return received.incrementAndGet();
    }

    /** Datagram from a source that is not allowed, or from a competing stream. */
    public long incrementRejected() {
        return rejected.incrementAndGet();
    }

    public long incrementMalformed() {
        return malformed.incrementAndGet();
    }

    /** Well-formed packet whose audio format does not match the receiver's configuration. */
    public long incrementFormatMismatch() {
        return formatMismatch.incrementAndGet();
    }

    public long incrementLate() {
        return late.incrementAndGet();
    }

    public long incrementDuplicate() {
        return duplicate.incrementAndGet();
    }

    public long incrementLost() {
        return lost.incrementAndGet();
    }

    public long incrementUnderruns() {
        return underruns.incrementAndGet();
    }

    public long incrementResyncs() {
        return resyncs.incrementAndGet();
    }

    public long incrementOverflowDrops() {
        return overflowDrops.incrementAndGet();
    }

    public long sent() {
        return sent.get();
    }

    public long sendErrors() {
        return sendErrors.get();
    }

    public long received() {
        return received.get();
    }

    public long rejected() {
        return rejected.get();
    }

    public long malformed() {
        return malformed.get();
    }

    public long formatMismatch() {
        return formatMismatch.get();
    }

    public long late() {
        return late.get();
    }

    public long duplicate() {
        return duplicate.get();
    }

    public long lost() {
        return lost.get();
    }

    public long underruns() {
        return underruns.get();
    }

    public long resyncs() {
        return resyncs.get();
    }

    public long overflowDrops() {
        return overflowDrops.get();
    }

    /** Records the peak sample magnitude (0..32768) of a captured frame. */
    public void recordCapturePeak(int peak) {
        recordPeak(capturePeak, peak);
    }

    /** Records the peak sample magnitude (0..32768) of a frame of received audio that was played. */
    public void recordPlaybackPeak(int peak) {
        recordPeak(playbackPeak, peak);
    }

    /** Highest capture peak since the last {@link #report}, or -1 if capture is not measured. */
    public int capturePeak() {
        return capturePeak.get();
    }

    /** Highest playback peak since the last {@link #report}, or -1 if playback is not measured. */
    public int playbackPeak() {
        return playbackPeak.get();
    }

    /**
     * Counters plus peak levels for the period since the previous report; resets the period peaks. Intended
     * for a single periodic reporter.
     */
    public String report() {
        StringBuilder sb = new StringBuilder(toString());
        appendPeak(sb, "capturePeak", capturePeak);
        appendPeak(sb, "playbackPeak", playbackPeak);
        return sb.toString();
    }

    private static void recordPeak(AtomicInteger target, int peak) {
        if (peak < 0 || peak > PcmLevels.FULL_SCALE) {
            throw new IllegalArgumentException("peak must be in [0, " + PcmLevels.FULL_SCALE + "], was " + peak);
        }
        target.accumulateAndGet(peak, Math::max);
    }

    private static void appendPeak(StringBuilder sb, String label, AtomicInteger peak) {
        // Once measured, a period with no frames reads as silent (0) rather than not measured.
        int value = peak.getAndUpdate(v -> v == NOT_MEASURED ? NOT_MEASURED : 0);
        if (value != NOT_MEASURED) {
            sb.append(' ').append(label).append('=').append(PcmLevels.formatDbfs(value).replace(" ", ""));
        }
    }

    @Override
    public String toString() {
        return "sent=" + sent() + " sendErrors=" + sendErrors()
                + " received=" + received() + " rejected=" + rejected() + " malformed=" + malformed()
                + " formatMismatch=" + formatMismatch() + " late=" + late() + " duplicate=" + duplicate()
                + " lost=" + lost() + " underruns=" + underruns() + " resyncs=" + resyncs()
                + " overflowDrops=" + overflowDrops();
    }
}
