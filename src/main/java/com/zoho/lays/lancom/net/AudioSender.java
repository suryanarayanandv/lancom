package com.zoho.lays.lancom.net;

import com.zoho.lays.lancom.audio.AudioSource;
import com.zoho.lays.lancom.config.AudioSettings;
import com.zoho.lays.lancom.config.SenderConfig;
import com.zoho.lays.lancom.protocol.AudioPacket;
import com.zoho.lays.lancom.protocol.PacketCodec;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Reads fixed-size frames from an {@link AudioSource} and sends each as one UDP datagram. The source's
 * blocking reads pace transmission to the capture clock. Transient network errors (receiver down, Wi-Fi
 * blip) are counted and logged but never stop the stream.
 */
public final class AudioSender extends StreamNode {
    private static final Logger LOG = Logger.getLogger(AudioSender.class.getName());
    private static final int SEND_BUFFER_BYTES = 1 << 20;
    private static final int SEND_ERROR_LOG_INTERVAL = 500;

    private final SenderConfig config;
    private final AudioSource source;
    private final int streamId;
    private DatagramChannel channel;

    public AudioSender(SenderConfig config, AudioSource source) {
        this.config = Objects.requireNonNull(config, "config");
        this.source = Objects.requireNonNull(source, "source");
        this.streamId = new SecureRandom().nextInt();
    }

    public int streamId() {
        return streamId;
    }

    @Override
    public synchronized void start() throws IOException {
        beginStart();
        DatagramChannel opened;
        try {
            opened = DatagramChannel.open();
            channel = opened;
            opened.setOption(StandardSocketOptions.SO_SNDBUF, SEND_BUFFER_BYTES);
            opened.connect(config.target());
            source.open();
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
        startWorker("lancom-sender", () -> streamLoop(opened));
        AudioSettings audio = config.audio();
        if (PacketCodec.HEADER_SIZE + audio.payloadBytes() > 1472) {
            LOG.fine("Datagrams exceed a typical Ethernet MTU and will be IP-fragmented; "
                    + "use a shorter --frame-ms on lossy networks");
        }
        LOG.info(() -> String.format("Streaming to %s (stream %08x, %s)", config.target(), streamId, audio));
    }

    private void streamLoop(DatagramChannel datagramChannel) throws IOException {
        AudioSettings audio = config.audio();
        byte[] frame = new byte[audio.payloadBytes()];
        ByteBuffer datagram = ByteBuffer.allocate(PacketCodec.HEADER_SIZE + frame.length);
        int sequence = 0;
        long samplePosition = 0;
        while (isRunning()) {
            if (!readFrame(frame)) {
                if (isRunning()) {
                    LOG.info("Audio source ended; stopping sender");
                    close();
                }
                return;
            }
            AudioPacket packet = new AudioPacket(streamId, sequence, samplePosition, audio.sampleRate(),
                    audio.channels(), AudioSettings.BITS_PER_SAMPLE, frame);
            datagram.clear();
            PacketCodec.encode(packet, datagram);
            datagram.flip();
            send(datagramChannel, datagram);
            sequence++;
            samplePosition += audio.samplesPerPacket();
        }
    }

    private boolean readFrame(byte[] frame) throws IOException {
        int filled = 0;
        while (filled < frame.length) {
            int read = source.read(frame, filled, frame.length - filled);
            if (read < 0 || !isRunning()) {
                return false;
            }
            filled += read;
        }
        return true;
    }

    private void send(DatagramChannel datagramChannel, ByteBuffer datagram) throws ClosedChannelException {
        try {
            datagramChannel.write(datagram);
            stats.incrementSent();
        } catch (ClosedChannelException e) {
            throw e;
        } catch (IOException e) {
            // e.g. PortUnreachableException when the receiver is not running yet, or a transient route failure.
            long errors = stats.incrementSendErrors();
            if (errors == 1 || errors % SEND_ERROR_LOG_INTERVAL == 0) {
                LOG.warning(() -> "Send to " + config.target() + " failed (" + errors + " total): " + e);
            }
        }
    }

    @Override
    protected void releaseResources() {
        closeQuietly(channel);
        closeQuietly(source);
    }
}
