package com.zoho.lays.lancom.cli;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliParserTest {

    @Test
    void parsesCommandOptionsAndFlags() throws Exception {
        CliArguments args = CliParser.parse("-v", "send", "--target", "127.0.0.1:5000", "--frame-ms=20");
        assertEquals(Optional.of("send"), args.command());
        assertEquals(Optional.of("127.0.0.1:5000"), args.option("target"));
        assertEquals(20, args.intOption("frame-ms", 10));
        assertEquals(48_000, args.intOption("sample-rate", 48_000));
        assertTrue(args.hasFlag(CliParser.FLAG_VERBOSE));
    }

    @Test
    void helpFlagWorksWithoutCommand() throws Exception {
        assertTrue(CliParser.parse("--help").hasFlag(CliParser.FLAG_HELP));
        assertTrue(CliParser.parse("-h").hasFlag(CliParser.FLAG_HELP));
    }

    @Test
    void rejectsInvalidUsage() {
        assertRejected("bogus");
        assertRejected("send", "--nope", "x");
        assertRejected("send", "--target");
        assertRejected("send", "--target", "--device");
        assertRejected("send", "--target=");
        assertRejected("send", "--target", "a:1", "--target", "b:2");
        assertRejected("send", "--port", "5000");            // receiver-only option
        assertRejected("receive", "--target", "a:1");        // sender-only option
        assertRejected("send", "extra-positional");
        assertRejected("--port", "1");                       // option without command
        assertRejected("-x");
        assertRejected("--");
        assertRejected("--help=yes");
        assertRejected("send", "--device", "bad\u0000name");
        assertRejected("send", "--device", "x".repeat(CliParser.MAX_VALUE_LENGTH + 1));
        assertThrows(CliException.class, () -> CliParser.parse((String) null));
    }

    @Test
    void rejectsNonNumericIntegerOption() throws Exception {
        CliArguments args = CliParser.parse("receive", "--port", "abc");
        assertThrows(CliException.class, () -> args.intOption("port", 1));
    }

    private static void assertRejected(String... argv) {
        assertThrows(CliException.class, () -> CliParser.parse(argv), () -> String.join(" ", argv));
    }
}
