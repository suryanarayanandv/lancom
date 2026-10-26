package com.zoho.lays.lancom.metrics;

import java.util.concurrent.atomic.AtomicLong;

/** Thread-safe counters describing the health of an audio stream. */
public final class StreamStats {
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

    @Override
    public String toString() {
        return "sent=" + sent() + " sendErrors=" + sendErrors()
                + " received=" + received() + " rejected=" + rejected() + " malformed=" + malformed()
                + " formatMismatch=" + formatMismatch() + " late=" + late() + " duplicate=" + duplicate()
                + " lost=" + lost() + " underruns=" + underruns() + " resyncs=" + resyncs()
                + " overflowDrops=" + overflowDrops();
    }
}
