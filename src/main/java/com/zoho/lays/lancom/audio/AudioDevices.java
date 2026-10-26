package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enumerates and selects Java Sound devices. Bluetooth headphones appear as ordinary output mixers once
 * paired and connected at the OS level, so selecting them is a matter of choosing the right mixer.
 *
 * <p>Device capabilities are independent of the wire format: a device qualifies if it supports 16-bit PCM
 * at the configured sample rate in mono <em>or</em> stereo. Channel differences are bridged by
 * {@link PcmChannelConverter}; see {@link #chooseChannels}.
 */
public final class AudioDevices {

    /** Channel counts LanCom can convert between. */
    public static final List<Integer> CONVERTIBLE_CHANNELS = List.of(1, 2);

    public enum Direction {
        CAPTURE(TargetDataLine.class, "capture"),
        PLAYBACK(SourceDataLine.class, "playback");

        private final Class<? extends DataLine> lineClass;
        private final String label;

        Direction(Class<? extends DataLine> lineClass, String label) {
            this.lineClass = lineClass;
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A device and the channel counts it supports at a given sample rate. */
    public static final class DeviceInfo {
        private final Mixer.Info mixer;
        private final List<Integer> channels;

        DeviceInfo(Mixer.Info mixer, List<Integer> channels) {
            this.mixer = mixer;
            this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        }

        public Mixer.Info mixer() {
            return mixer;
        }

        public String name() {
            return mixer.getName();
        }

        /** Supported channel counts, ascending; never empty. */
        public List<Integer> channels() {
            return channels;
        }
    }

    private AudioDevices() {
    }

    /** 16-bit signed little-endian PCM, the only sample format LanCom uses. */
    public static AudioFormat pcmFormat(int sampleRate, int channels) {
        return new AudioFormat(sampleRate, AudioSettings.BITS_PER_SAMPLE, channels, true, false);
    }

    /** Devices that support {@code sampleRate} in at least one convertible channel count. */
    public static List<DeviceInfo> list(Direction direction, int sampleRate) {
        List<DeviceInfo> result = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            List<Integer> channels = supportedChannels(direction, Optional.of(info), sampleRate);
            if (!channels.isEmpty()) {
                result.add(new DeviceInfo(info, channels));
            }
        }
        return result;
    }

    /**
     * Channel counts the device (or the system default when {@code mixer} is empty) supports at
     * {@code sampleRate}. Empty if none, or if the device is inaccessible.
     */
    public static List<Integer> supportedChannels(Direction direction, Optional<Mixer.Info> mixer, int sampleRate) {
        List<Integer> result = new ArrayList<>();
        for (int channels : CONVERTIBLE_CHANNELS) {
            DataLine.Info lineInfo = new DataLine.Info(direction.lineClass, pcmFormat(sampleRate, channels));
            try {
                boolean supported = mixer.isPresent()
                        ? AudioSystem.getMixer(mixer.get()).isLineSupported(lineInfo)
                        : AudioSystem.isLineSupported(lineInfo);
                if (supported) {
                    result.add(channels);
                }
            } catch (IllegalArgumentException | SecurityException e) {
                // Mixer vanished or is inaccessible; treat as unsupported.
            }
        }
        return result;
    }

    /**
     * Picks the device channel count: the wire channel count when supported (no conversion), otherwise the
     * first supported alternative.
     *
     * @throws AudioDeviceException if the device supports no convertible channel count
     */
    public static int chooseChannels(int wireChannels, List<Integer> supported, String deviceDescription)
            throws AudioDeviceException {
        if (supported.contains(wireChannels)) {
            return wireChannels;
        }
        for (int channels : supported) {
            if (CONVERTIBLE_CHANNELS.contains(channels)) {
                return channels;
            }
        }
        throw new AudioDeviceException(deviceDescription + " does not support 16-bit PCM in mono or stereo at the "
                + "requested sample rate. Run 'lancom devices --sample-rate HZ' to see what each device supports, "
                + "or try another --sample-rate.");
    }

    /**
     * Resolves a user-supplied device name. An exact (case-insensitive) match wins; otherwise the query must
     * match exactly one device as a substring.
     *
     * @return the mixer, or empty when {@code query} is empty (meaning the system default)
     */
    public static Optional<Mixer.Info> find(Direction direction, int sampleRate, Optional<String> query)
            throws AudioDeviceException {
        if (query.isEmpty()) {
            return Optional.empty();
        }
        String needle = query.get().toLowerCase(Locale.ROOT);
        List<Mixer.Info> candidates = list(direction, sampleRate).stream()
                .map(DeviceInfo::mixer)
                .collect(Collectors.toList());
        for (Mixer.Info info : candidates) {
            if (info.getName().toLowerCase(Locale.ROOT).equals(needle)) {
                return Optional.of(info);
            }
        }
        List<Mixer.Info> matches = candidates.stream()
                .filter(info -> info.getName().toLowerCase(Locale.ROOT).contains(needle))
                .collect(Collectors.toList());
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        String available = describe(matches.isEmpty() ? candidates : matches);
        if (matches.isEmpty()) {
            throw new AudioDeviceException("No " + direction.label() + " device matching '" + query.get()
                    + "' supports 16-bit PCM at " + sampleRate + " Hz. Available: " + available);
        }
        throw new AudioDeviceException("Device name '" + query.get() + "' is ambiguous; matches: " + available);
    }

    /** Opens a data line on the selected mixer, or on the system default when {@code mixer} is empty. */
    static <T extends Line> T openLine(Class<T> lineClass, Direction direction, AudioFormat format,
                                       Optional<Mixer.Info> mixer) throws LineUnavailableException {
        DataLine.Info info = new DataLine.Info(direction.lineClass, format);
        Line line = mixer.isPresent() ? AudioSystem.getMixer(mixer.get()).getLine(info) : AudioSystem.getLine(info);
        return lineClass.cast(line);
    }

    static String displayName(Optional<Mixer.Info> mixer, Direction direction) {
        return mixer.map(Mixer.Info::getName)
                .orElse(direction == Direction.CAPTURE ? "system default input" : "system default output");
    }

    /** "mono", "stereo" or "mono, stereo". */
    public static String describeChannels(List<Integer> channels) {
        return channels.stream().map(AudioDevices::channelLabel).collect(Collectors.joining(", "));
    }

    public static String channelLabel(int channels) {
        switch (channels) {
            case 1:
                return "mono";
            case 2:
                return "stereo";
            default:
                return channels + " ch";
        }
    }

    private static String describe(List<Mixer.Info> devices) {
        if (devices.isEmpty()) {
            return "(none)";
        }
        return devices.stream().map(info -> "'" + info.getName() + "'").collect(Collectors.joining(", "));
    }
}
