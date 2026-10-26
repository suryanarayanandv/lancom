package com.zoho.lays.lancom.audio;

import java.io.IOException;
import java.util.Objects;

/**
 * Adapts an {@link AudioSink} that accepts one channel count to callers writing another, e.g. a mono stream
 * played on headphones that only accept stereo. Writes must contain whole sample frames and come from a
 * single thread; {@link #close} may be called from any thread.
 */
public final class ChannelConvertingSink implements AudioSink {
    private final AudioSink delegate;
    private final int inputChannels;
    private final int deviceChannels;
    private final int inputFrameBytes;
    private byte[] scratch = new byte[0];

    public ChannelConvertingSink(AudioSink delegate, int inputChannels, int deviceChannels) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.inputFrameBytes = PcmChannelConverter.bytesFor(1, inputChannels);
        PcmChannelConverter.bytesFor(1, deviceChannels); // validates
        this.inputChannels = inputChannels;
        this.deviceChannels = deviceChannels;
    }

    @Override
    public void open() throws IOException {
        delegate.open();
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        if (length % inputFrameBytes != 0) {
            throw new IllegalArgumentException("Write of " + length + " bytes is not a whole number of "
                    + inputFrameBytes + "-byte frames");
        }
        int frames = length / inputFrameBytes;
        int outBytes = PcmChannelConverter.bytesFor(frames, deviceChannels);
        if (scratch.length < outBytes) {
            scratch = new byte[outBytes];
        }
        PcmChannelConverter.convert(buffer, offset, frames, inputChannels, scratch, 0, deviceChannels);
        delegate.write(scratch, 0, outBytes);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
