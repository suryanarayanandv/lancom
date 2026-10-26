package com.zoho.lays.lancom.audio;

import com.zoho.lays.lancom.config.AudioSettings;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Captures audio from a Java Sound input device. The device is opened in the wire channel count when it
 * supports it; otherwise (e.g. a mono-only laptop microphone with a stereo stream) it is opened in a channel
 * count it does support and converted, so callers always receive audio in {@code settings} format.
 */
public final class JavaSoundSource implements AudioSource {
    private static final Logger LOG = Logger.getLogger(JavaSoundSource.class.getName());
    /** Device buffer size in packets: small for latency, large enough to absorb scheduling hiccups. */
    private static final int LINE_BUFFER_PACKETS = 4;

    private final AudioSettings settings;
    private final Optional<String> deviceQuery;
    private volatile TargetDataLine line;
    private volatile AudioSource reader;

    public JavaSoundSource(AudioSettings settings, Optional<String> deviceQuery) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.deviceQuery = Objects.requireNonNull(deviceQuery, "deviceQuery");
    }

    @Override
    public synchronized void open() throws IOException {
        if (line != null) {
            throw new IllegalStateException("Capture device already open");
        }
        AudioDevices.Direction direction = AudioDevices.Direction.CAPTURE;
        Optional<Mixer.Info> mixer = AudioDevices.find(direction, settings.sampleRate(), deviceQuery);
        String name = AudioDevices.displayName(mixer, direction);
        List<Integer> supported = AudioDevices.supportedChannels(direction, mixer, settings.sampleRate());
        int deviceChannels = AudioDevices.chooseChannels(settings.channels(), supported,
                "Capture device " + name);
        AudioFormat format = AudioDevices.pcmFormat(settings.sampleRate(), deviceChannels);
        int bufferBytes = PcmChannelConverter.bytesFor(settings.samplesPerPacket(), deviceChannels)
                * LINE_BUFFER_PACKETS;

        TargetDataLine opened = null;
        try {
            opened = AudioDevices.openLine(TargetDataLine.class, direction, format, mixer);
            opened.open(format, bufferBytes);
            opened.start();
        } catch (LineUnavailableException | IllegalArgumentException | SecurityException e) {
            if (opened != null) {
                opened.close();
            }
            throw new AudioDeviceException("Cannot open capture device " + name + " (" + format + "): "
                    + e.getMessage() + ". Check microphone permission for the app running lancom.", e);
        }
        AudioSource lineReader = new LineReader(opened);
        reader = deviceChannels == settings.channels()
                ? lineReader
                : new ChannelConvertingSource(lineReader, deviceChannels, settings.channels());
        line = opened;
        LOG.info(() -> "Capturing from " + name + " (" + describeConversion(deviceChannels) + ")");
    }

    private String describeConversion(int deviceChannels) {
        String device = AudioDevices.channelLabel(deviceChannels) + " @ " + settings.sampleRate() + " Hz";
        return deviceChannels == settings.channels()
                ? device
                : device + ", converted to " + AudioDevices.channelLabel(settings.channels());
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, buffer.length);
        AudioSource current = reader;
        if (current == null) {
            throw new IllegalStateException("Capture device not open");
        }
        return current.read(buffer, offset, length);
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

    /** Raw reads from the line in the device's own format. */
    private static final class LineReader implements AudioSource {
        private final TargetDataLine line;

        LineReader(TargetDataLine line) {
            this.line = line;
        }

        @Override
        public void open() {
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            // TargetDataLine requires whole frames; round down, but always request at least one frame.
            int frameSize = line.getFormat().getFrameSize();
            int request = Math.max(frameSize, length - length % frameSize);
            if (request > length) {
                throw new IllegalArgumentException("Read of " + length + " bytes is smaller than one "
                        + frameSize + "-byte frame");
            }
            int read = line.read(buffer, offset, request);
            if (read <= 0 && !line.isOpen()) {
                return -1;
            }
            return read;
        }

        @Override
        public void close() {
            line.close();
        }
    }
}
