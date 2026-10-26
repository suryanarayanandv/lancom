package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import java.time.Duration;
import java.util.Objects;

/**
 * Detects sustained digital silence (all-zero frames), which almost always means a capture device is not
 * delivering audio, e.g. because the OS denied microphone access. Not thread-safe; confine to one thread.
 */
public final class SilenceDetector {

    public enum Event {
        /** Nothing worth reporting. */
        NONE,
        /** Silence has just lasted the threshold duration. */
        SILENCE_DETECTED,
        /** Silence continues; emitted once per repeat interval after detection. */
        STILL_SILENT,
        /** Audio returned after silence had been reported. */
        AUDIO_RESUMED
    }

    private final long thresholdFrames;
    private final long repeatFrames;
    private long silentFrames;
    private boolean reported;

    public SilenceDetector(long thresholdFrames, long repeatFrames) {
        if (thresholdFrames < 1 || repeatFrames < 1) {
            throw new IllegalArgumentException("thresholdFrames and repeatFrames must be >= 1");
        }
        this.thresholdFrames = thresholdFrames;
        this.repeatFrames = repeatFrames;
    }

    public static SilenceDetector forDurations(AudioSettings audio, Duration threshold, Duration repeat) {
        Objects.requireNonNull(audio, "audio");
        long frameMs = audio.frameDurationMs();
        return new SilenceDetector(Math.max(1, ceilDiv(threshold.toMillis(), frameMs)),
                Math.max(1, ceilDiv(repeat.toMillis(), frameMs)));
    }

    public Event onFrame(boolean silent) {
        if (!silent) {
            boolean wasReported = reported;
            silentFrames = 0;
            reported = false;
            return wasReported ? Event.AUDIO_RESUMED : Event.NONE;
        }
        silentFrames++;
        if (silentFrames == thresholdFrames) {
            reported = true;
            return Event.SILENCE_DETECTED;
        }
        if (reported && (silentFrames - thresholdFrames) % repeatFrames == 0) {
            return Event.STILL_SILENT;
        }
        return Event.NONE;
    }

    public void reset() {
        silentFrames = 0;
        reported = false;
    }

    public long silentFrames() {
        return silentFrames;
    }

    private static long ceilDiv(long a, long b) {
        return -Math.floorDiv(-a, b);
    }
}
