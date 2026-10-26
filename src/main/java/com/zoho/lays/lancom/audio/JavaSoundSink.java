package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Plays audio on a Java Sound output device, e.g. speakers or connected Bluetooth headphones. The device is
 * opened in the wire channel count when supported; otherwise audio is converted to a channel count the
 * device does support.
 */
public final class JavaSoundSink implements AudioSink {
    private static final Logger LOG = Logger.getLogger(JavaSoundSink.class.getName());
    /** Device buffer size in packets. Adds to end-to-end latency, so keep it small. */
    private static final int LINE_BUFFER_PACKETS = 3;

    private final AudioSettings settings;
    private final Optional<String> deviceQuery;
    private volatile SourceDataLine line;
    private volatile AudioSink writer;

    public JavaSoundSink(AudioSettings settings, Optional<String> deviceQuery) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.deviceQuery = Objects.requireNonNull(deviceQuery, "deviceQuery");
    }

    @Override
    public synchronized void open() throws IOException {
        if (line != null) {
            throw new IllegalStateException("Playback device already open");
        }
        AudioDevices.Direction direction = AudioDevices.Direction.PLAYBACK;
        Optional<Mixer.Info> mixer = AudioDevices.find(direction, settings.sampleRate(), deviceQuery);
        String name = AudioDevices.displayName(mixer, direction);
        List<Integer> supported = AudioDevices.supportedChannels(direction, mixer, settings.sampleRate());
        int deviceChannels = AudioDevices.chooseChannels(settings.channels(), supported,
                "Playback device " + name);
        AudioFormat format = AudioDevices.pcmFormat(settings.sampleRate(), deviceChannels);
        int bufferBytes = PcmChannelConverter.bytesFor(settings.samplesPerPacket(), deviceChannels)
                * LINE_BUFFER_PACKETS;

        SourceDataLine opened = null;
        try {
            opened = AudioDevices.openLine(SourceDataLine.class, direction, format, mixer);
            opened.open(format, bufferBytes);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (opened != null) {
                opened.close();
            }
            throw new AudioDeviceException("Cannot open playback device " + name + " (" + format + "): "
                    + e.getMessage(), e);
        }
        AudioSink lineWriter = new LineWriter(opened);
        writer = deviceChannels == settings.channels()
                ? lineWriter
                : new ChannelConvertingSink(lineWriter, settings.channels(), deviceChannels);
        line = opened;
        LOG.info(() -> "Playing to " + name + " (" + describeConversion(deviceChannels) + ")");
    }

    private String describeConversion(int deviceChannels) {
        String device = AudioDevices.channelLabel(deviceChannels) + " @ " + settings.sampleRate() + " Hz";
        return deviceChannels == settings.channels()
                ? device
                : device + ", converted from " + AudioDevices.channelLabel(settings.channels());
    }

    @Override
    public void write(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        AudioSink current = writer;
        if (current == null) {
            throw new IllegalStateException("Playback device not open");
        }
        current.write(buffer, offset, length);
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

    /** Blocking writes to the line in the device's own format. */
    private static final class LineWriter implements AudioSink {
        private final SourceDataLine line;

        LineWriter(SourceDataLine line) {
            this.line = line;
        }

        @Override
        public void open() {
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            int position = offset;
            int remaining = length;
            while (remaining > 0) {
                int written = line.write(buffer, position, remaining);
                if (written <= 0 && !line.isOpen()) {
                    throw new AudioDeviceException("Playback device closed");
                }
                position += written;
                remaining -= written;
            }
        }

        @Override
        public void close() {
            line.close();
        }
    }
}
