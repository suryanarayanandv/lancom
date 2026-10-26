package com.zoho.lays.lancom.cli;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Parsed command line: an optional command, its options and global flags. */
public final class CliArguments {
    private final String command;
    private final Map<String, String> options;
    private final Set<String> flags;

    CliArguments(String command, Map<String, String> options, Set<String> flags) {
        this.command = command;
        this.options = Map.copyOf(options);
        this.flags = Set.copyOf(flags);
    }

    public Optional<String> command() {
        return Optional.ofNullable(command);
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name));
    }

    public int intOption(String name, int defaultValue) throws CliException {
        String value = options.get(name);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new CliException("Option --" + name + " expects an integer, got '" + value + "'", e);
        }
    }
}
