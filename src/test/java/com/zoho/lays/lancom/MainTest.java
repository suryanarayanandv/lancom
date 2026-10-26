package com.zoho.lays.lancom;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private final Main main = new Main(new PrintStream(out, true, StandardCharsets.UTF_8),
            new PrintStream(err, true, StandardCharsets.UTF_8));

    @Test
    void helpPrintsUsage() {
        assertEquals(Main.EXIT_OK, main.run(new String[] {"--help"}));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void versionPrintsVersion() {
        assertEquals(Main.EXIT_OK, main.run(new String[] {"--version"}));
        assertTrue(out.toString(StandardCharsets.UTF_8).startsWith("lancom "));
    }

    @Test
    void noCommandIsUsageError() {
        assertEquals(Main.EXIT_USAGE, main.run(new String[0]));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void invalidArgumentsAreUsageErrors() {
        assertEquals(Main.EXIT_USAGE, main.run(new String[] {"bogus"}));
        assertEquals(Main.EXIT_USAGE, main.run(new String[] {"send"}));
        assertEquals(Main.EXIT_USAGE, main.run(new String[] {"receive", "--port", "-1"}));
        assertTrue(err.toString(StandardCharsets.UTF_8).contains("error:"));
    }
}
