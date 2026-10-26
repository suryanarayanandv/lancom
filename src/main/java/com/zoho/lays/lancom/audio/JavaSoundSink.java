package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/** Plays audio on a Java Sound output device, e.g. speakers or connected Bluetooth headphones. */
public final class JavaSoundSink implements AudioSink {
    private static final Logger LOG = Logger.getLogger(JavaSoundSink.class.getName());
    /** Device buffer size in packets. Adds to end-to-end latency, so keep it small. */
    private static final int LINE_BUFFER_PACKETS = 3;

    private final AudioSettings settings;
    private final Optional<String> deviceQuery;
    private volatile SourceDataLine line;

    public JavaSoundSink(AudioSettings settings, Optional<String> deviceQuery) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.deviceQuery = Objects.requireNonNull(deviceQuery, "deviceQuery");
    }

    @Override
    public synchronized void open() throws IOException {
        if (line != null) {
            throw new IllegalStateException("Playback device already open");
        }
        AudioFormat format = settings.toAudioFormat();
        Optional<Mixer.Info> mixer = AudioDevices.find(AudioDevices.Direction.PLAYBACK, format, deviceQuery);
        SourceDataLine opened = null;
        try {
            opened = AudioDevices.openLine(SourceDataLine.class, AudioDevices.Direction.PLAYBACK, format, mixer);
            opened.open(format, settings.payloadBytes() * LINE_BUFFER_PACKETS);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (opened != null) {
                opened.close();
            }
            throw new AudioDeviceException("Cannot open playback device "
                    + mixer.map(Mixer.Info::getName).orElse("(system default)") + " for " + settings + ": "
                    + e.getMessage(), e);
        }
        line = opened;
        LOG.info(() -> "Playing to " + mixer.map(Mixer.Info::getName).orElse("system default output"));
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        SourceDataLine current = line;
        if (current == null) {
            throw new IllegalStateException("Playback device not open");
        }
        int position = offset;
        int remaining = length;
        while (remaining > 0) {
            int written = current.write(buffer, position, remaining);
            if (written <= 0 && !current.isOpen()) {
                throw new AudioDeviceException("Playback device closed");
            }
            position += written;
            remaining -= written;
        }
    }

    @Override
    public synchronized void close() {
        SourceDataLine current = line;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
    }
}
