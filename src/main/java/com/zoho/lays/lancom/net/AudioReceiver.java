package com.zoho.lays.lancom.net;

import com.zoho.lays.lancom.audio.AudioSink;
import com.zoho.lays.lancom.buffer.JitterBuffer;
import com.zoho.lays.lancom.config.AudioSettings;
import com.zoho.lays.lancom.config.ReceiverConfig;
import com.zoho.lays.lancom.protocol.AudioPacket;
import com.zoho.lays.lancom.protocol.MalformedPacketException;
import com.zoho.lays.lancom.protocol.PacketCodec;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Receives audio datagrams, validates them, and plays them through an {@link AudioSink} via a
 * {@link JitterBuffer}.
 *
 * <p>Two threads are used: a network thread that validates and enqueues packets, and a playback thread that
 * dequeues one frame at a time and writes it to the sink, whose blocking writes pace playback.
 *
 * <p>Security: every datagram is untrusted. Sources can be restricted with an allowlist; packets are
 * strictly validated; and the receiver locks onto a single stream, ignoring competing streams until the
 * active one has been idle for the configured timeout.
 */
public final class AudioReceiver extends StreamNode {
    private static final Logger LOG = Logger.getLogger(AudioReceiver.class.getName());
    private static final int RECEIVE_BUFFER_BYTES = 1 << 20;
    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(5);

    private final ReceiverConfig config;
    private final AudioSink sink;
    private final JitterBuffer jitterBuffer;
    private final long streamTimeoutNanos;
    private DatagramChannel channel;

    // Confined to the network thread.
    private boolean streamLocked;
    private int activeStreamId;
    private long lastAcceptedNanos;
    private long lastMismatchWarningNanos;

    public AudioReceiver(ReceiverConfig config, AudioSink sink) {
        this.config = Objects.requireNonNull(config, "config");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.jitterBuffer = new JitterBuffer(config.jitterTargetPackets(), config.jitterCapacityPackets(),
                config.audio().payloadBytes(), stats);
        this.streamTimeoutNanos = config.streamTimeout().toNanos();
    }

    @Override
    public synchronized void start() throws IOException {
        beginStart();
        DatagramChannel opened;
        try {
            opened = DatagramChannel.open();
            channel = opened;
            opened.setOption(StandardSocketOptions.SO_RCVBUF, RECEIVE_BUFFER_BYTES);
            opened.bind(config.bindAddress());
            sink.open();
        } catch (IOException | RuntimeException e) {
            close();
            throw e;
        }
        InetSocketAddress local = (InetSocketAddress) opened.getLocalAddress();
        startWorker("lancom-receiver-net", () -> receiveLoop(opened));
        startWorker("lancom-receiver-playback", this::playbackLoop);
        LOG.info(() -> String.format("Listening on %s (%s, jitter buffer %d ms = %d packets%s)",
                local, config.audio(), config.jitterMs(), config.jitterTargetPackets(),
                config.allowedSources().isEmpty() ? ", accepting any source" : ", allowlist " + config.allowedSources()));
    }

    /** The bound local address; useful when binding to port 0. */
    public synchronized InetSocketAddress localAddress() throws IOException {
        if (channel == null) {
            throw new IllegalStateException("Receiver not started");
        }
        return (InetSocketAddress) channel.getLocalAddress();
    }

    public int jitterDepth() {
        return jitterBuffer.depth();
    }

    private void receiveLoop(DatagramChannel datagramChannel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.MAX_DATAGRAM_SIZE);
        while (isRunning()) {
            buffer.clear();
            SocketAddress from = datagramChannel.receive(buffer);
            if (from == null) {
                continue;
            }
            buffer.flip();
            handleDatagram(buffer, (InetSocketAddress) from);
        }
    }

    private void handleDatagram(ByteBuffer datagram, InetSocketAddress from) {
        stats.incrementReceived();
        if (!config.isSourceAllowed(from.getAddress())) {
            stats.incrementRejected();
            LOG.fine(() -> "Rejected datagram from non-allowlisted source " + from);
            return;
        }
        AudioPacket packet;
        try {
            packet = PacketCodec.decode(datagram);
        } catch (MalformedPacketException e) {
            stats.incrementMalformed();
            LOG.fine(() -> "Malformed datagram from " + from + ": " + e.getMessage());
            return;
        }
        AudioSettings audio = config.audio();
        if (!audio.matches(packet)) {
            stats.incrementFormatMismatch();
            long now = System.nanoTime();
            if (now - lastMismatchWarningNanos > WARN_INTERVAL_NANOS || lastMismatchWarningNanos == 0) {
                lastMismatchWarningNanos = now;
                LOG.warning(() -> "Ignoring " + packet + " from " + from + ": receiver expects " + audio + " ("
                        + audio.payloadBytes() + " byte payloads). Use the same audio options on both ends.");
            }
            return;
        }
        long now = System.nanoTime();
        if (!streamLocked || packet.streamId() != activeStreamId) {
            if (streamLocked && now - lastAcceptedNanos < streamTimeoutNanos) {
                stats.incrementRejected();
                return;
            }
            jitterBuffer.reset();
            streamLocked = true;
            activeStreamId = packet.streamId();
            LOG.info(() -> String.format("Receiving stream %08x from %s", packet.streamId(), from));
        }
        lastAcceptedNanos = now;
        jitterBuffer.offer(packet);
    }

    private void playbackLoop() throws IOException {
        byte[] frame = new byte[config.audio().payloadBytes()];
        while (isRunning()) {
            jitterBuffer.take(frame);
            sink.write(frame, 0, frame.length);
        }
    }

    @Override
    protected void releaseResources() {
        closeQuietly(channel);
        closeQuietly(sink);
    }
}
