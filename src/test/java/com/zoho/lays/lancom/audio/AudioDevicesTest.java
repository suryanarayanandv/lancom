package com.zoho.lays.lancom.audio;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioDevicesTest {

    @Test
    void prefersWireChannelsWhenSupported() throws AudioDeviceException {
        assertEquals(2, AudioDevices.chooseChannels(2, List.of(1, 2), "dev"));
        assertEquals(1, AudioDevices.chooseChannels(1, List.of(1, 2), "dev"));
    }

    @Test
    void fallsBackToConvertibleChannels() throws AudioDeviceException {
        // Mono-only microphone with a stereo stream: the case that produced silence before.
        assertEquals(1, AudioDevices.chooseChannels(2, List.of(1), "MacBook Pro Microphone"));
        // Stereo-only headphones with a mono stream.
        assertEquals(2, AudioDevices.chooseChannels(1, List.of(2), "Headphones"));
    }

    @Test
    void failsWithActionableMessageWhenNothingIsSupported() {
        AudioDeviceException e = assertThrows(AudioDeviceException.class,
                () -> AudioDevices.chooseChannels(2, List.of(), "Capture device X"));
        assertTrue(e.getMessage().startsWith("Capture device X"));
        assertTrue(e.getMessage().contains("lancom devices"));
    }

    @Test
    void describesChannels() {
        assertEquals("mono, stereo", AudioDevices.describeChannels(List.of(1, 2)));
        assertEquals("stereo", AudioDevices.channelLabel(2));
        assertEquals("6 ch", AudioDevices.channelLabel(6));
    }

    @Test
    void pcmFormatIsSigned16BitLittleEndian() {
        var format = AudioDevices.pcmFormat(44_100, 1);
        assertEquals(44_100f, format.getSampleRate());
        assertEquals(16, format.getSampleSizeInBits());
        assertEquals(1, format.getChannels());
        assertFalse(format.isBigEndian());
    }
}
