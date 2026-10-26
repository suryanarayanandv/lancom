package com.zoho.lays.lancom.audio;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Objects;

/** Level measurement for signed 16-bit little-endian PCM. */
public final class PcmLevels {
    /** Full-scale magnitude for 16-bit audio. */
    public static final int FULL_SCALE = 32_768;

    private PcmLevels() {
    }

    /** Peak absolute sample value in {@code [0, 32768]}; 0 means digital silence. A trailing odd byte is ignored. */
    public static int peak(byte[] pcm, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, pcm.length);
        int peak = 0;
        int end = offset + (length & ~1);
        for (int i = offset; i < end; i += 2) {
            int sample = (short) ((pcm[i] & 0xff) | (pcm[i + 1] << 8));
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /** Peak of the bytes between the buffer's position and limit, without changing the position. */
    public static int peak(ByteBuffer pcm) {
        int peak = 0;
        int end = pcm.position() + (pcm.remaining() & ~1);
        for (int i = pcm.position(); i < end; i += 2) {
            int sample = (short) ((pcm.get(i) & 0xff) | (pcm.get(i + 1) << 8));
            peak = Math.max(peak, Math.abs(sample));
        }
        return peak;
    }

    /** Peak level in dBFS; negative infinity for silence. */
    public static double toDbfs(int peak) {
        if (peak < 0 || peak > FULL_SCALE) {
            throw new IllegalArgumentException("peak must be in [0, " + FULL_SCALE + "], was " + peak);
        }
        return peak == 0 ? Double.NEGATIVE_INFINITY : 20 * Math.log10((double) peak / FULL_SCALE);
    }

    /** Human-readable dBFS, e.g. {@code "-23.5 dBFS"} or {@code "silent"}. */
    public static String formatDbfs(int peak) {
        return peak == 0 ? "silent" : String.format(Locale.ROOT, "%.1f dBFS", toDbfs(peak));
    }
}
