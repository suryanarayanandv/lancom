package com.zoho.lays.lancom.cli;

import com.zoho.lays.lancom.config.ReceiverConfig;
import com.zoho.lays.lancom.config.SenderConfig;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigFactoryTest {

    @Test
    void parsesHostPortForms() throws Exception {
        assertEquals(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 5000),
                ConfigFactory.parseHostPort("127.0.0.1:5000"));
        assertEquals(new InetSocketAddress(InetAddress.getByName("::1"), 6000),
                ConfigFactory.parseHostPort("[::1]:6000"));
        assertEquals(7000, ConfigFactory.parseHostPort("localhost:7000").getPort());
    }

    @Test
    void rejectsInvalidHostPort() {
        for (String bad : new String[] {"127.0.0.1", ":5000", "host:", "host:abc", "host:0", "host:65536",
                "::1:5000", "[::1]5000", "[::1", "host.invalid:5000"}) {
            assertThrows(CliException.class, () -> ConfigFactory.parseHostPort(bad), bad);
        }
    }

    @Test
    void buildsSenderConfig() throws Exception {
        SenderConfig config = ConfigFactory.senderConfig(CliParser.parse(
                "send", "--target", "127.0.0.1:5000", "--device", "Mic", "--sample-rate", "16000",
                "--channels", "1", "--frame-ms", "20"));
        assertEquals(5000, config.target().getPort());
        assertEquals(Optional.of("Mic"), config.captureDevice());
        assertEquals(16_000, config.audio().sampleRate());
        assertEquals(1, config.audio().channels());
        assertEquals(20, config.audio().frameDurationMs());
    }

    @Test
    void senderRequiresTarget() {
        assertThrows(CliException.class, () -> ConfigFactory.senderConfig(CliParser.parse("send")));
    }

    @Test
    void buildsReceiverConfig() throws Exception {
        ReceiverConfig config = ConfigFactory.receiverConfig(CliParser.parse(
                "receive", "--port", "6000", "--bind", "127.0.0.1", "--allow", "127.0.0.1, 192.0.2.5",
                "--jitter-ms", "80", "--stream-timeout-ms", "3000", "--device", "Headphones"));
        assertEquals(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 6000), config.bindAddress());
        assertEquals(Set.of(InetAddress.getByName("127.0.0.1"), InetAddress.getByName("192.0.2.5")),
                config.allowedSources());
        assertEquals(80, config.jitterMs());
        assertEquals(Duration.ofSeconds(3), config.streamTimeout());
        assertEquals(Optional.of("Headphones"), config.playbackDevice());
    }

    @Test
    void receiverRejectsInvalidValues() {
        assertRejected("receive", "--port", "0");
        assertRejected("receive", "--port", "70000");
        assertRejected("receive", "--allow", "127.0.0.1,,10.0.0.1");
        assertRejected("receive", "--jitter-ms", "1");
        assertRejected("receive", "--stream-timeout-ms", "1");
        assertRejected("receive", "--sample-rate", "12345");
        assertRejected("receive", "--bind", "host.invalid");
    }

    private static void assertRejected(String... argv) {
        assertThrows(CliException.class, () -> ConfigFactory.receiverConfig(CliParser.parse(argv)),
                () -> String.join(" ", argv));
    }
}
