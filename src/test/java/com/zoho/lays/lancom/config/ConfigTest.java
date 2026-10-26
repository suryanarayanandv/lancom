package com.zoho.lays.lancom.config;

import com.zoho.lays.lancom.protocol.AudioPacket;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigTest {

    @Test
    void audioSettingsDerivesFrameSizes() {
        AudioSettings settings = new AudioSettings(48_000, 2, 10);
        assertEquals(480, settings.samplesPerPacket());
        assertEquals(4, settings.bytesPerSampleFrame());
        assertEquals(1_920, settings.payloadBytes());
        assertEquals(48_000f, settings.toAudioFormat().getSampleRate());
        assertFalse(settings.toAudioFormat().isBigEndian());
    }

    @Test
    void audioSettingsRejectsUnsupportedValues() {
        assertThrows(IllegalArgumentException.class, () -> new AudioSettings(22_050, 2, 10));
        assertThrows(IllegalArgumentException.class, () -> new AudioSettings(48_000, 3, 10));
        assertThrows(IllegalArgumentException.class, () -> new AudioSettings(48_000, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> new AudioSettings(48_000, 2, 7));
        // 44.1 kHz * 5 ms = 220.5 samples: not a whole number.
        assertThrows(IllegalArgumentException.class, () -> new AudioSettings(44_100, 2, 5));
    }

    @Test
    void audioSettingsMatchesOnlyIdenticalFormat() {
        AudioSettings settings = new AudioSettings(8_000, 1, 5);
        assertTrue(settings.matches(new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[80])));
        assertFalse(settings.matches(new AudioPacket(1, 0, 0, 16_000, 1, 16, new byte[80])));
        assertFalse(settings.matches(new AudioPacket(1, 0, 0, 8_000, 2, 16, new byte[80])));
        assertFalse(settings.matches(new AudioPacket(1, 0, 0, 8_000, 1, 16, new byte[40])));
    }

    @Test
    void senderConfigRequiresResolvedTarget() {
        assertThrows(IllegalArgumentException.class, () -> SenderConfig.builder().build());
        assertThrows(IllegalArgumentException.class, () -> SenderConfig.builder()
                .target(InetSocketAddress.createUnresolved("nowhere.invalid", 5000)).build());
        assertThrows(IllegalArgumentException.class, () -> SenderConfig.builder()
                .target(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)).build());
    }

    @Test
    void senderConfigNormalizesDeviceName() {
        InetSocketAddress target = new InetSocketAddress(InetAddress.getLoopbackAddress(), 5000);
        assertEquals(Optional.empty(), SenderConfig.builder().target(target).captureDevice("   ").build().captureDevice());
        assertEquals(Optional.of("USB Mic"),
                SenderConfig.builder().target(target).captureDevice("  USB Mic ").build().captureDevice());
        assertThrows(IllegalArgumentException.class,
                () -> SenderConfig.builder().target(target).captureDevice("x".repeat(257)).build());
    }

    @Test
    void receiverConfigDefaults() {
        ReceiverConfig config = ReceiverConfig.builder().build();
        assertEquals(ReceiverConfig.DEFAULT_PORT, config.bindAddress().getPort());
        assertEquals(6, config.jitterTargetPackets()); // 60 ms / 10 ms
        assertTrue(config.jitterCapacityPackets() > config.jitterTargetPackets() * 2);
        assertTrue(config.isSourceAllowed(InetAddress.getLoopbackAddress()));
    }

    @Test
    void receiverConfigRoundsJitterUpToWholePackets() {
        ReceiverConfig config = ReceiverConfig.builder().jitterMs(25).build();
        assertEquals(3, config.jitterTargetPackets());
    }

    @Test
    void receiverConfigEnforcesAllowlist() throws Exception {
        InetAddress allowed = InetAddress.getByName("192.0.2.10");
        ReceiverConfig config = ReceiverConfig.builder().allowedSources(List.of(allowed)).build();
        assertTrue(config.isSourceAllowed(allowed));
        assertFalse(config.isSourceAllowed(InetAddress.getByName("192.0.2.11")));
    }

    @Test
    void receiverConfigValidatesRanges() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ReceiverConfig.builder().jitterMs(5).build());
        assertThrows(IllegalArgumentException.class,
                () -> ReceiverConfig.builder().jitterMs(ReceiverConfig.MAX_JITTER_MS + 1).build());
        assertThrows(IllegalArgumentException.class,
                () -> ReceiverConfig.builder().streamTimeout(Duration.ofMillis(10)).build());
        assertThrows(IllegalArgumentException.class,
                () -> ReceiverConfig.builder().streamTimeout(Duration.ofHours(1)).build());
        assertThrows(IllegalArgumentException.class, () -> ReceiverConfig.builder()
                .bindAddress(InetSocketAddress.createUnresolved("nowhere.invalid", 1)).build());

        List<InetAddress> tooMany = new ArrayList<>();
        for (int i = 0; i <= ReceiverConfig.MAX_ALLOWED_SOURCES; i++) {
            tooMany.add(InetAddress.getByName("10.0.0." + (i + 1)));
        }
        assertThrows(IllegalArgumentException.class, () -> ReceiverConfig.builder().allowedSources(tooMany).build());
    }
}
