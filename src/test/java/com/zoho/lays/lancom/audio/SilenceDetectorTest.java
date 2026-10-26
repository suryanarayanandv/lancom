package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.audio.SilenceDetector.Event;
import com.zoho.lays.lancom.config.AudioSettings;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SilenceDetectorTest {

    @Test
    void reportsOnceAtThresholdThenAtRepeatInterval() {
        SilenceDetector detector = new SilenceDetector(3, 2);
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            events.add(detector.onFrame(true));
        }
        assertEquals(List.of(Event.NONE, Event.NONE, Event.SILENCE_DETECTED, Event.NONE, Event.STILL_SILENT,
                Event.NONE, Event.STILL_SILENT, Event.NONE), events);
    }

    @Test
    void audioBeforeThresholdResetsCountSilently() {
        SilenceDetector detector = new SilenceDetector(3, 10);
        detector.onFrame(true);
        detector.onFrame(true);
        assertEquals(Event.NONE, detector.onFrame(false));
        assertEquals(0, detector.silentFrames());
        assertEquals(Event.NONE, detector.onFrame(true));
        assertEquals(Event.NONE, detector.onFrame(true));
        assertEquals(Event.SILENCE_DETECTED, detector.onFrame(true));
    }

    @Test
    void reportsAudioResumedOnlyAfterDetection() {
        SilenceDetector detector = new SilenceDetector(1, 10);
        assertEquals(Event.SILENCE_DETECTED, detector.onFrame(true));
        assertEquals(Event.AUDIO_RESUMED, detector.onFrame(false));
        assertEquals(Event.NONE, detector.onFrame(false));
    }

    @Test
    void neverReportsForContinuousAudio() {
        SilenceDetector detector = new SilenceDetector(1, 1);
        for (int i = 0; i < 1_000; i++) {
            assertEquals(Event.NONE, detector.onFrame(false));
        }
    }

    @Test
    void derivesFrameCountsFromDurations() {
        AudioSettings audio = new AudioSettings(48_000, 1, 20);
        SilenceDetector detector = SilenceDetector.forDurations(audio, Duration.ofMillis(50), Duration.ofSeconds(1));
        // ceil(50 / 20) = 3 frames
        assertEquals(Event.NONE, detector.onFrame(true));
        assertEquals(Event.NONE, detector.onFrame(true));
        assertEquals(Event.SILENCE_DETECTED, detector.onFrame(true));
    }

    @Test
    void rejectsInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new SilenceDetector(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new SilenceDetector(1, 0));
    }
}
