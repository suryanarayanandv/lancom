package com.zoho.lays.lancom.audio;

import java.util.Objects;

/**
 * Converts interleaved signed 16-bit little-endian PCM between mono and stereo.
 *
 * <p>Mono to stereo duplicates each sample into both channels. Stereo to mono averages the two channels,
 * rounding toward negative infinity; the result always fits in 16 bits, so no clipping is needed.
 */
public final class PcmChannelConverter {
    public static final int BYTES_PER_SAMPLE = 2;

    private PcmChannelConverter() {
    }

    /** Bytes needed for {@code frames} sample frames with {@code channels} channels. */
    public static int bytesFor(int frames, int channels) {
        requireChannels(channels, "channels");
        if (frames < 0) {
            throw new IllegalArgumentException("frames must be >= 0, was " + frames);
        }
        return Math.multiplyExact(frames, channels * BYTES_PER_SAMPLE);
    }

    /**
     * Converts {@code frames} sample frames from {@code source} to {@code destination}. The two regions must
     * not overlap unless the channel counts are equal.
     *
     * @return number of bytes written to {@code destination}
     */
    public static int convert(byte[] source, int sourceOffset, int frames, int sourceChannels,
                              byte[] destination, int destinationOffset, int destinationChannels) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");
        int sourceBytes = bytesFor(frames, sourceChannels);
        int destinationBytes = bytesFor(frames, destinationChannels);
        Objects.checkFromIndexSize(sourceOffset, sourceBytes, source.length);
        Objects.checkFromIndexSize(destinationOffset, destinationBytes, destination.length);

        if (sourceChannels == destinationChannels) {
            System.arraycopy(source, sourceOffset, destination, destinationOffset, sourceBytes);
        } else if (sourceChannels == 1) {
            monoToStereo(source, sourceOffset, frames, destination, destinationOffset);
        } else {
            stereoToMono(source, sourceOffset, frames, destination, destinationOffset);
        }
        return destinationBytes;
    }

    private static void monoToStereo(byte[] src, int srcOffset, int frames, byte[] dst, int dstOffset) {
        for (int i = 0; i < frames; i++) {
            byte lo = src[srcOffset + i * 2];
            byte hi = src[srcOffset + i * 2 + 1];
            int out = dstOffset + i * 4;
            dst[out] = lo;
            dst[out + 1] = hi;
            dst[out + 2] = lo;
            dst[out + 3] = hi;
        }
    }

    private static void stereoToMono(byte[] src, int srcOffset, int frames, byte[] dst, int dstOffset) {
        for (int i = 0; i < frames; i++) {
            int in = srcOffset + i * 4;
            int left = (short) ((src[in] & 0xff) | (src[in + 1] << 8));
            int right = (short) ((src[in + 2] & 0xff) | (src[in + 3] << 8));
            int mixed = (left + right) >> 1;
            dst[dstOffset + i * 2] = (byte) mixed;
            dst[dstOffset + i * 2 + 1] = (byte) (mixed >> 8);
        }
    }

    private static void requireChannels(int channels, String name) {
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException(name + " must be 1 or 2, was " + channels);
        }
    }
}
