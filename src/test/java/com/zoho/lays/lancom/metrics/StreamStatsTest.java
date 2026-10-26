package com.zoho.lays.lancom.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamStatsTest {

    @Test
    void peaksAreOmittedUntilMeasured() {
        String report = new StreamStats().report();
        assertFalse(report.contains("capturePeak"));
        assertFalse(report.contains("playbackPeak"));
    }

    @Test
    void reportShowsPeriodMaximumThenResetsToSilent() {
        StreamStats stats = new StreamStats();
        stats.recordCapturePeak(100);
        stats.recordCapturePeak(16_384);
        stats.recordCapturePeak(5);
        assertEquals(16_384, stats.capturePeak());
        assertTrue(stats.report().contains("capturePeak=-6.0dBFS"));
        // Next period with no frames recorded reads as silent, not absent.
        assertTrue(stats.report().contains("capturePeak=silent"));
        assertFalse(stats.report().contains("playbackPeak"));
    }

    @Test
    void reportsSilenceExplicitly() {
        StreamStats stats = new StreamStats();
        stats.recordPlaybackPeak(0);
        assertTrue(stats.report().contains("playbackPeak=silent"));
    }

    @Test
    void rejectsOutOfRangePeaks() {
        StreamStats stats = new StreamStats();
        assertThrows(IllegalArgumentException.class, () -> stats.recordCapturePeak(-1));
        assertThrows(IllegalArgumentException.class, () -> stats.recordPlaybackPeak(32_769));
    }
}
