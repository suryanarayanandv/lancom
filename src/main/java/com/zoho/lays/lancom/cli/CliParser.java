package com.zoho.lays.lancom.cli;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Strict command-line parser. Unknown commands/options, duplicates, missing values and options that do not
 * apply to the chosen command are all rejected.
 *
 * <p>Syntax: {@code lancom [flags] <command> [--option value | --option=value]...}
 */
public final class CliParser {
    public static final String CMD_DEVICES = "devices";
    public static final String CMD_SEND = "send";
    public static final String CMD_RECEIVE = "receive";

    public static final String FLAG_HELP = "help";
    public static final String FLAG_VERBOSE = "verbose";
    public static final String FLAG_VERSION = "version";

    public static final String OPT_TARGET = "target";
    public static final String OPT_PORT = "port";
    public static final String OPT_BIND = "bind";
    public static final String OPT_DEVICE = "device";
    public static final String OPT_ALLOW = "allow";
    public static final String OPT_JITTER_MS = "jitter-ms";
    public static final String OPT_STREAM_TIMEOUT_MS = "stream-timeout-ms";
    public static final String OPT_SAMPLE_RATE = "sample-rate";
    public static final String OPT_CHANNELS = "channels";
    public static final String OPT_FRAME_MS = "frame-ms";

    static final int MAX_VALUE_LENGTH = 1024;

    private static final Set<String> FLAGS = Set.of(FLAG_HELP, FLAG_VERBOSE, FLAG_VERSION);
    private static final Map<String, Set<String>> COMMAND_OPTIONS = Map.of(
            CMD_DEVICES, Set.of(OPT_SAMPLE_RATE, OPT_CHANNELS),
            CMD_SEND, Set.of(OPT_TARGET, OPT_DEVICE, OPT_SAMPLE_RATE, OPT_CHANNELS, OPT_FRAME_MS),
            CMD_RECEIVE, Set.of(OPT_PORT, OPT_BIND, OPT_DEVICE, OPT_ALLOW, OPT_JITTER_MS, OPT_STREAM_TIMEOUT_MS,
                    OPT_SAMPLE_RATE, OPT_CHANNELS, OPT_FRAME_MS));
    private static final Set<String> ALL_OPTIONS = allOptions();

    private CliParser() {
    }

    public static CliArguments parse(String... args) throws CliException {
        Objects.requireNonNull(args, "args");
        String command = null;
        Map<String, String> options = new LinkedHashMap<>();
        Set<String> flags = new HashSet<>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                throw new CliException("Null argument at position " + i);
            }
            if (arg.equals("-h")) {
                flags.add(FLAG_HELP);
            } else if (arg.equals("-v")) {
                flags.add(FLAG_VERBOSE);
            } else if (arg.startsWith("--")) {
                String body = arg.substring(2);
                int equals = body.indexOf('=');
                String name = equals >= 0 ? body.substring(0, equals) : body;
                String value = equals >= 0 ? body.substring(equals + 1) : null;
                if (name.isEmpty()) {
                    throw new CliException("Malformed option '" + arg + "'");
                }
                if (FLAGS.contains(name)) {
                    if (value != null) {
                        throw new CliException("Flag --" + name + " does not take a value");
                    }
                    flags.add(name);
                    continue;
                }
                if (!ALL_OPTIONS.contains(name)) {
                    throw new CliException("Unknown option --" + name);
                }
                if (value == null) {
                    if (i + 1 >= args.length || args[i + 1] == null || args[i + 1].startsWith("--")) {
                        throw new CliException("Missing value for --" + name);
                    }
                    value = args[++i];
                }
                validateValue(name, value);
                if (options.putIfAbsent(name, value) != null) {
                    throw new CliException("Option --" + name + " given more than once");
                }
            } else if (arg.startsWith("-") && arg.length() > 1) {
                throw new CliException("Unknown option " + arg);
            } else if (command == null) {
                if (!COMMAND_OPTIONS.containsKey(arg)) {
                    throw new CliException("Unknown command '" + arg + "'");
                }
                command = arg;
            } else {
                throw new CliException("Unexpected argument '" + arg + "'");
            }
        }

        if (command != null) {
            Set<String> allowed = COMMAND_OPTIONS.get(command);
            for (String name : options.keySet()) {
                if (!allowed.contains(name)) {
                    throw new CliException("Option --" + name + " is not valid for '" + command + "'");
                }
            }
        } else if (!options.isEmpty() && !flags.contains(FLAG_HELP)) {
            throw new CliException("Options require a command");
        }
        return new CliArguments(command, options, flags);
    }

    private static void validateValue(String name, String value) throws CliException {
        if (value.isBlank()) {
            throw new CliException("Empty value for --" + name);
        }
        if (value.length() > MAX_VALUE_LENGTH) {
            throw new CliException("Value for --" + name + " exceeds " + MAX_VALUE_LENGTH + " characters");
        }
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                throw new CliException("Value for --" + name + " contains control characters");
            }
        }
    }

    private static Set<String> allOptions() {
        Set<String> all = new HashSet<>();
        COMMAND_OPTIONS.values().forEach(all::addAll);
        return Set.copyOf(all);
    }
}
