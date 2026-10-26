package com.zoho.lays.lancom.cli;

import com.zoho.lays.lancom.config.AudioSettings;
import com.zoho.lays.lancom.config.ReceiverConfig;
import com.zoho.lays.lancom.config.SenderConfig;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Converts parsed command-line arguments into validated configuration objects. */
public final class ConfigFactory {

    private ConfigFactory() {
    }

    public static AudioSettings audioSettings(CliArguments args) throws CliException {
        int sampleRate = args.intOption(CliParser.OPT_SAMPLE_RATE, AudioSettings.DEFAULT_SAMPLE_RATE);
        int channels = args.intOption(CliParser.OPT_CHANNELS, AudioSettings.DEFAULT_CHANNELS);
        int frameMs = args.intOption(CliParser.OPT_FRAME_MS, AudioSettings.DEFAULT_FRAME_DURATION_MS);
        try {
            return new AudioSettings(sampleRate, channels, frameMs);
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage(), e);
        }
    }

    public static SenderConfig senderConfig(CliArguments args) throws CliException {
        String target = args.option(CliParser.OPT_TARGET)
                .orElseThrow(() -> new CliException("Missing required option --target HOST:PORT"));
        AudioSettings audio = audioSettings(args);
        try {
            return SenderConfig.builder()
                    .target(parseHostPort(target))
                    .audio(audio)
                    .captureDevice(args.option(CliParser.OPT_DEVICE).orElse(null))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage(), e);
        }
    }

    public static ReceiverConfig receiverConfig(CliArguments args) throws CliException {
        int port = args.intOption(CliParser.OPT_PORT, ReceiverConfig.DEFAULT_PORT);
        if (port < 1 || port > 65_535) {
            throw new CliException("Port must be between 1 and 65535, was " + port);
        }
        InetAddress bind = args.option(CliParser.OPT_BIND).isPresent()
                ? parseAddress(args.option(CliParser.OPT_BIND).get())
                : null;
        List<InetAddress> allowed = args.option(CliParser.OPT_ALLOW).isPresent()
                ? parseAddressList(args.option(CliParser.OPT_ALLOW).get())
                : List.of();
        int jitterMs = args.intOption(CliParser.OPT_JITTER_MS, ReceiverConfig.DEFAULT_JITTER_MS);
        int timeoutMs = args.intOption(CliParser.OPT_STREAM_TIMEOUT_MS,
                (int) ReceiverConfig.DEFAULT_STREAM_TIMEOUT.toMillis());
        AudioSettings audio = audioSettings(args);
        try {
            return ReceiverConfig.builder()
                    .bindAddress(bind == null ? new InetSocketAddress(port) : new InetSocketAddress(bind, port))
                    .audio(audio)
                    .playbackDevice(args.option(CliParser.OPT_DEVICE).orElse(null))
                    .allowedSources(allowed)
                    .jitterMs(jitterMs)
                    .streamTimeout(Duration.ofMillis(timeoutMs))
                    .build();
        } catch (IllegalArgumentException e) {
            throw new CliException(e.getMessage(), e);
        }
    }

    /** Parses {@code host:port}, {@code ipv4:port} or {@code [ipv6]:port} and resolves the host. */
    static InetSocketAddress parseHostPort(String value) throws CliException {
        String text = value.trim();
        String host;
        String portText;
        if (text.startsWith("[")) {
            int close = text.indexOf(']');
            if (close < 0 || close + 1 >= text.length() || text.charAt(close + 1) != ':') {
                throw new CliException("Invalid address '" + value + "'; expected [IPv6]:PORT");
            }
            host = text.substring(1, close);
            portText = text.substring(close + 2);
        } else {
            int colon = text.lastIndexOf(':');
            if (colon < 0) {
                throw new CliException("Invalid address '" + value + "'; expected HOST:PORT");
            }
            host = text.substring(0, colon);
            portText = text.substring(colon + 1);
            if (host.indexOf(':') >= 0) {
                throw new CliException("IPv6 addresses must be bracketed, e.g. [::1]:50005");
            }
        }
        if (host.isEmpty()) {
            throw new CliException("Missing host in '" + value + "'");
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            throw new CliException("Invalid port in '" + value + "'", e);
        }
        if (port < 1 || port > 65_535) {
            throw new CliException("Port must be between 1 and 65535, was " + port);
        }
        return new InetSocketAddress(parseAddress(host), port);
    }

    static InetAddress parseAddress(String value) throws CliException {
        String host = value.trim();
        if (host.isEmpty()) {
            throw new CliException("Empty address");
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException | SecurityException e) {
            throw new CliException("Cannot resolve host '" + host + "'", e);
        }
    }

    static List<InetAddress> parseAddressList(String value) throws CliException {
        List<InetAddress> result = new ArrayList<>();
        for (String part : value.split(",")) {
            if (part.isBlank()) {
                throw new CliException("Empty entry in address list '" + value + "'");
            }
            result.add(parseAddress(part));
            if (result.size() > ReceiverConfig.MAX_ALLOWED_SOURCES) {
                throw new CliException("At most " + ReceiverConfig.MAX_ALLOWED_SOURCES + " allowed sources are supported");
            }
        }
        return result;
    }
}
