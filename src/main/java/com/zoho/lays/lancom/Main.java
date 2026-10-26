package com.zoho.lays.lancom;

import com.zoho.lays.lancom.audio.AudioDevices;
import com.zoho.lays.lancom.audio.JavaSoundSink;
import com.zoho.lays.lancom.audio.JavaSoundSource;
import com.zoho.lays.lancom.cli.CliArguments;
import com.zoho.lays.lancom.cli.CliException;
import com.zoho.lays.lancom.cli.CliParser;
import com.zoho.lays.lancom.cli.ConfigFactory;
import com.zoho.lays.lancom.config.AudioSettings;
import com.zoho.lays.lancom.config.ReceiverConfig;
import com.zoho.lays.lancom.config.SenderConfig;
import com.zoho.lays.lancom.logging.LoggingSetup;
import com.zoho.lays.lancom.net.AudioReceiver;
import com.zoho.lays.lancom.net.AudioSender;
import com.zoho.lays.lancom.net.StreamNode;

import javax.sound.sampled.Mixer;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Command-line entry point for the LanCom audio relay. */
public final class Main {
    static final int EXIT_OK = 0;
    static final int EXIT_FAILURE = 1;
    static final int EXIT_USAGE = 2;

    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static final Duration STATS_INTERVAL = Duration.ofSeconds(10);

    static final String USAGE = String.join(System.lineSeparator(),
            "LanCom - LAN audio relay",
            "",
            "Usage:",
            "  lancom devices [--sample-rate HZ] [--channels N]",
            "  lancom send    --target HOST:PORT [--device NAME] [audio options]",
            "  lancom receive [--port PORT] [--bind ADDR] [--device NAME] [--allow IP[,IP...]]",
            "                 [--jitter-ms MS] [--stream-timeout-ms MS] [audio options]",
            "",
            "Commands:",
            "  devices   List capture and playback devices and their supported channels",
            "  send      Capture audio and stream it to a receiver",
            "  receive   Receive a stream and play it (e.g. on Bluetooth headphones)",
            "",
            "Audio options (must be identical on sender and receiver):",
            "  --sample-rate HZ   8000|16000|24000|32000|44100|48000 (default " + AudioSettings.DEFAULT_SAMPLE_RATE + ")",
            "  --channels N       Stream channels, 1 or 2 (default " + AudioSettings.DEFAULT_CHANNELS + ").",
            "                     Devices with other channel counts are converted automatically.",
            "  --frame-ms MS      5|10|20|40 (default " + AudioSettings.DEFAULT_FRAME_DURATION_MS + ")",
            "",
            "Receiver options:",
            "  --port PORT              UDP port (default " + ReceiverConfig.DEFAULT_PORT + ")",
            "  --bind ADDR              Local address to bind (default: all interfaces)",
            "  --allow IP[,IP...]       Only accept audio from these sources (recommended)",
            "  --jitter-ms MS           Jitter buffer depth (default " + ReceiverConfig.DEFAULT_JITTER_MS + ")",
            "  --stream-timeout-ms MS   Idle time before another sender may take over (default "
                    + ReceiverConfig.DEFAULT_STREAM_TIMEOUT.toMillis() + ")",
            "",
            "Device selection:",
            "  --device NAME      Exact or partial device name from 'lancom devices'.",
            "                     Pair and connect Bluetooth headphones in the OS first.",
            "",
            "Global options:",
            "  -h, --help         Show this help",
            "  -v, --verbose      Enable debug logging",
            "      --version      Print the version",
            "");

    private final PrintStream out;
    private final PrintStream err;

    Main(PrintStream out, PrintStream err) {
        this.out = Objects.requireNonNull(out, "out");
        this.err = Objects.requireNonNull(err, "err");
    }

    public static void main(String[] args) {
        LoggingSetup.configure(Level.INFO);
        System.exit(new Main(System.out, System.err).run(args));
    }

    int run(String[] args) {
        CliArguments cli;
        try {
            cli = CliParser.parse(args);
        } catch (CliException e) {
            return usageError(e);
        }
        if (cli.hasFlag(CliParser.FLAG_VERBOSE)) {
            LoggingSetup.configure(Level.FINE);
        }
        if (cli.hasFlag(CliParser.FLAG_HELP)) {
            out.print(USAGE);
            return EXIT_OK;
        }
        if (cli.hasFlag(CliParser.FLAG_VERSION)) {
            out.println("lancom " + version());
            return EXIT_OK;
        }
        Optional<String> command = cli.command();
        if (command.isEmpty()) {
            err.print(USAGE);
            return EXIT_USAGE;
        }
        try {
            switch (command.get()) {
                case CliParser.CMD_DEVICES:
                    return listDevices(ConfigFactory.audioSettings(cli));
                case CliParser.CMD_SEND: {
                    SenderConfig config = ConfigFactory.senderConfig(cli);
                    return runUntilStopped(new AudioSender(config,
                            new JavaSoundSource(config.audio(), config.captureDevice())));
                }
                case CliParser.CMD_RECEIVE: {
                    ReceiverConfig config = ConfigFactory.receiverConfig(cli);
                    return runUntilStopped(new AudioReceiver(config,
                            new JavaSoundSink(config.audio(), config.playbackDevice())));
                }
                default:
                    throw new CliException("Unknown command '" + command.get() + "'");
            }
        } catch (CliException e) {
            return usageError(e);
        } catch (IOException e) {
            LOG.log(Level.FINE, "Command failed", e);
            err.println("error: " + e.getMessage());
            return EXIT_FAILURE;
        }
    }

    private int runUntilStopped(StreamNode node) throws IOException {
        Thread shutdownHook = new Thread(node::close, "lancom-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
        try {
            node.start();
            CompletableFuture<Void> termination = node.termination();
            while (true) {
                try {
                    termination.get(STATS_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                    return EXIT_OK;
                } catch (TimeoutException e) {
                    String report = node.stats().report();
                    LOG.info(() -> "Stats: " + report);
                } catch (ExecutionException e) {
                    err.println("error: " + e.getCause().getMessage());
                    return EXIT_FAILURE;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return EXIT_OK;
                }
            }
        } finally {
            node.close();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down; the hook is running or has run.
            }
        }
    }

    private int listDevices(AudioSettings settings) {
        int rate = settings.sampleRate();
        printDevices("Capture devices", AudioDevices.list(AudioDevices.Direction.CAPTURE, rate), rate);
        out.println();
        printDevices("Playback devices", AudioDevices.list(AudioDevices.Direction.PLAYBACK, rate), rate);
        out.println();
        out.println("Channel differences are converted automatically (e.g. a mono microphone on a stereo stream).");
        return EXIT_OK;
    }

    private void printDevices(String title, List<AudioDevices.DeviceInfo> devices, int sampleRate) {
        out.println(title + " supporting 16-bit PCM at " + sampleRate + " Hz:");
        if (devices.isEmpty()) {
            out.println("  (none)");
        }
        for (AudioDevices.DeviceInfo device : devices) {
            Mixer.Info info = device.mixer();
            out.println("  - " + info.getName() + "  (" + AudioDevices.describeChannels(device.channels())
                    + ")  [" + info.getVendor() + ", " + info.getDescription() + "]");
        }
    }

    private int usageError(CliException e) {
        err.println("error: " + e.getMessage());
        err.println("Run 'lancom --help' for usage.");
        return EXIT_USAGE;
    }

    static String version() {
        String version = Main.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }
}
