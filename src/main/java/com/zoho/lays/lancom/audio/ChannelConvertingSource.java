package com.zoho.lays.lancom.audio;

import java.io.IOException;
import java.util.Objects;

/**
 * Adapts an {@link AudioSource} that produces one channel count (e.g. a mono-only microphone) to another
 * (e.g. a stereo stream). Handles delegates that return partial frames and callers that request fewer
 * bytes than one output frame. Reads must come from a single thread; {@link #close} may be called from any.
 */
public final class ChannelConvertingSource implements AudioSource {
    private final AudioSource delegate;
    private final int sourceChannels;
    private final int targetChannels;
    private final int sourceFrameBytes;
    private final int targetFrameBytes;

    /** Device bytes; {@code [0, carry)} holds an incomplete frame left over from the previous read. */
    private byte[] scratch = new byte[0];
    private int carry;
    /** One converted frame that did not fit in the caller's buffer. */
    private final byte[] pending;
    private int pendingOffset;
    private int pendingLength;

    public ChannelConvertingSource(AudioSource delegate, int sourceChannels, int targetChannels) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.sourceFrameBytes = PcmChannelConverter.bytesFor(1, sourceChannels);
        this.targetFrameBytes = PcmChannelConverter.bytesFor(1, targetChannels);
        this.sourceChannels = sourceChannels;
        this.targetChannels = targetChannels;
        this.pending = new byte[targetFrameBytes];
    }

    @Override
    public void open() throws IOException {
        delegate.open();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length == 0) {
            return 0;
        }
        if (pendingLength > 0) {
            int count = Math.min(length, pendingLength);
            System.arraycopy(pending, pendingOffset, buffer, offset, count);
            pendingOffset += count;
            pendingLength -= count;
            return count;
        }

        int frames = Math.max(1, length / targetFrameBytes);
        int needed = Math.multiplyExact(frames, sourceFrameBytes);
        if (scratch.length < needed) {
            byte[] grown = new byte[needed];
            System.arraycopy(scratch, 0, grown, 0, carry);
            scratch = grown;
        }
        int available = carry;
        while (available < sourceFrameBytes) {
            int read = delegate.read(scratch, available, needed - available);
            if (read < 0) {
                carry = 0;
                return -1;
            }
            available += read;
        }
        // Blocks only until at least one whole device frame is available, then converts what is there.
        int wholeFrames = available / sourceFrameBytes;
        int consumed = wholeFrames * sourceFrameBytes;
        int produced = wholeFrames * targetFrameBytes;
        int result;
        if (produced <= length) {
            PcmChannelConverter.convert(scratch, 0, wholeFrames, sourceChannels, buffer, offset, targetChannels);
            result = produced;
        } else {
            // Only possible when the caller asked for less than one output frame (wholeFrames == 1).
            PcmChannelConverter.convert(scratch, 0, 1, sourceChannels, pending, 0, targetChannels);
            System.arraycopy(pending, 0, buffer, offset, length);
            pendingOffset = length;
            pendingLength = targetFrameBytes - length;
            result = length;
        }
        carry = available - consumed;
        System.arraycopy(scratch, consumed, scratch, 0, carry);
        return result;
    }

    @Override
    public void close() {
        delegate.close();
    }
}
