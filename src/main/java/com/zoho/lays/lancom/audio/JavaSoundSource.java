package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/** Captures audio from a Java Sound input device. */
public final class JavaSoundSource implements AudioSource {
    private static final Logger LOG = Logger.getLogger(JavaSoundSource.class.getName());
    /** Device buffer size in packets: small for latency, large enough to absorb scheduling hiccups. */
    private static final int LINE_BUFFER_PACKETS = 4;

    private final AudioSettings settings;
    private final Optional<String> deviceQuery;
    private volatile TargetDataLine line;

    public JavaSoundSource(AudioSettings settings, Optional<String> deviceQuery) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.deviceQuery = Objects.requireNonNull(deviceQuery, "deviceQuery");
    }

    @Override
    public synchronized void open() throws IOException {
        if (line != null) {
            throw new IllegalStateException("Capture device already open");
        }
        AudioFormat format = settings.toAudioFormat();
        Optional<Mixer.Info> mixer = AudioDevices.find(AudioDevices.Direction.CAPTURE, format, deviceQuery);
        TargetDataLine opened = null;
        try {
            opened = AudioDevices.openLine(TargetDataLine.class, AudioDevices.Direction.CAPTURE, format, mixer);
            opened.open(format, settings.payloadBytes() * LINE_BUFFER_PACKETS);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (opened != null) {
                opened.close();
            }
            throw new AudioDeviceException("Cannot open capture device "
                    + mixer.map(Mixer.Info::getName).orElse("(system default)") + " for " + settings + ": "
                    + e.getMessage() + ". Check microphone permissions, or try --channels 1 / another --sample-rate.", e);
        }
        line = opened;
        LOG.info(() -> "Capturing from " + mixer.map(Mixer.Info::getName).orElse("system default input"));
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        TargetDataLine current = line;
        if (current == null) {
            throw new IllegalStateException("Capture device not open");
        }
        int read = current.read(buffer, offset, length);
        if (read <= 0 && !current.isOpen()) {
            return -1;
        }
        return read;
    }

    @Override
    public synchronized void close() {
        TargetDataLine current = line;
        if (current != null) {
            current.stop();
            current.flush();
            current.close();
        }
    }
}
