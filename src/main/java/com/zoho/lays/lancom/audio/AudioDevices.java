package com.zoho.lays.lancom.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Enumerates and selects Java Sound devices. Bluetooth headphones appear as ordinary output mixers once
 * paired and connected at the OS level, so selecting them is a matter of choosing the right mixer.
 */
public final class AudioDevices {

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

    private AudioDevices() {
    }

    /** Devices that can handle {@code format} in the given direction. */
    public static List<Mixer.Info> list(Direction direction, AudioFormat format) {
        DataLine.Info lineInfo = new DataLine.Info(direction.lineClass, format);
        List<Mixer.Info> result = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(info).isLineSupported(lineInfo)) {
                    result.add(info);
                }
            } catch (IllegalArgumentException | SecurityException e) {
                // Mixer vanished or is inaccessible; skip it.
            }
        }
        return result;
    }

    /**
     * Resolves a user-supplied device name. An exact (case-insensitive) match wins; otherwise the query must
     * match exactly one device as a substring.
     *
     * @return the mixer, or empty when {@code query} is empty (meaning the system default)
     */
    public static Optional<Mixer.Info> find(Direction direction, AudioFormat format, Optional<String> query)
            throws AudioDeviceException {
        if (query.isEmpty()) {
            return Optional.empty();
        }
        String needle = query.get().toLowerCase(Locale.ROOT);
        List<Mixer.Info> candidates = list(direction, format);
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
                    + "' supports " + format + ". Available: " + available);
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

    private static String describe(List<Mixer.Info> devices) {
        if (devices.isEmpty()) {
            return "(none)";
        }
        return devices.stream().map(info -> "'" + info.getName() + "'").collect(Collectors.joining(", "));
    }
}
