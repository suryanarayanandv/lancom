package com.zoho.lays.lancom.net;

import com.zoho.lays.lancom.audio.AudioSink;
import com.zoho.lays.lancom.audio.AudioSource;
import com.zoho.lays.lancom.config.AudioSettings;
import com.zoho.lays.lancom.config.ReceiverConfig;
import com.zoho.lays.lancom.config.SenderConfig;
import com.zoho.lays.lancom.protocol.AudioPacket;
import com.zoho.lays.lancom.protocol.PacketCodec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** End-to-end tests over the loopback interface with fake audio devices. */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AudioStreamIntegrationTest {
    private static final AudioSettings AUDIO = new AudioSettings(8_000, 1, 5); // 80-byte frames
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final List<AutoCloseable> resources = new ArrayList<>();

    @AfterEach
    void tearDown() throws Exception {
        for (AutoCloseable resource : resources) {
            resource.close();
        }
    }

    @Test
    void streamsAudioInOrderFromSenderToReceiver() throws Exception {
        RecordingSink sink = new RecordingSink();
        AudioReceiver receiver = startReceiver(receiverConfig().build(), sink);

        int frames = 200;
        AudioSender sender = track(new AudioSender(SenderConfig.builder()
                .target(new InetSocketAddress(LOOPBACK, receiver.localAddress().getPort()))
                .audio(AUDIO)
                .build(), new SequenceSource(frames)));
        sender.start();

        // The sender stops on its own when the source ends; termination completes normally.
        sender.termination().get(15, TimeUnit.SECONDS);
        assertFalse(sender.isRunning());
        assertEquals(frames, sender.stats().sent());

        awaitCondition(() -> sink.frameIds.size() >= frames * 9 / 10, "receiver plays most frames");
        List<Integer> received = new ArrayList<>(sink.frameIds);
        for (int i = 1; i < received.size(); i++) {
            assertTrue(received.get(i) > received.get(i - 1), "frames must play in order: " + received);
        }
        assertEquals(0, receiver.stats().malformed());
        assertEquals(0, receiver.stats().formatMismatch());
    }

    @Test
    void dropsMalformedDatagrams() throws Exception {
        AudioReceiver receiver = startReceiver(receiverConfig().build(), new RecordingSink());
        try (DatagramChannel attacker = DatagramChannel.open()) {
            attacker.send(ByteBuffer.wrap(new byte[] {1, 2, 3}), receiver.localAddress());
            attacker.send(ByteBuffer.wrap(new byte[PacketCodec.HEADER_SIZE + 10]), receiver.localAddress());
        }
        awaitCondition(() -> receiver.stats().malformed() == 2, "malformed datagrams counted");
        assertEquals(0, receiver.jitterDepth());
        assertTrue(receiver.isRunning(), "receiver must survive malformed input");
    }

    @Test
    void dropsPacketsWithMismatchedFormat() throws Exception {
        AudioReceiver receiver = startReceiver(receiverConfig().build(), new RecordingSink());
        sendRaw(receiver, new AudioPacket(7, 0, 0, 16_000, 1, 16, new byte[160]));
        awaitCondition(() -> receiver.stats().formatMismatch() == 1, "format mismatch counted");
        assertEquals(0, receiver.jitterDepth());
    }

    @Test
    void rejectsSourcesNotOnAllowlist() throws Exception {
        AudioReceiver receiver = startReceiver(receiverConfig()
                .allowedSources(List.of(InetAddress.getByName("192.0.2.1")))
                .build(), new RecordingSink());
        sendRaw(receiver, validPacket(1, 0));
        awaitCondition(() -> receiver.stats().rejected() == 1, "non-allowlisted packet rejected");
        assertEquals(0, receiver.jitterDepth());
    }

    @Test
    void locksOntoFirstStreamUntilItTimesOut() throws Exception {
        AudioReceiver receiver = startReceiver(receiverConfig()
                .jitterMs(40) // large enough that nothing is played during the test window
                .streamTimeout(Duration.ofMillis(300))
                .build(), new RecordingSink());

        sendRaw(receiver, validPacket(1, 0));
        awaitCondition(() -> receiver.jitterDepth() == 1, "first stream accepted");

        sendRaw(receiver, validPacket(2, 0));
        awaitCondition(() -> receiver.stats().rejected() == 1, "competing stream rejected");
        assertEquals(1, receiver.jitterDepth());

        Thread.sleep(400);
        sendRaw(receiver, validPacket(2, 0));
        awaitCondition(() -> receiver.stats().rejected() == 1 && receiver.jitterDepth() == 1
                && receiver.stats().received() == 3, "second stream takes over after timeout");
    }

    @Test
    void sinkFailureTerminatesReceiverExceptionally() throws Exception {
        RecordingSink sink = new RecordingSink();
        sink.failOnWrite.set(true);
        AudioReceiver receiver = startReceiver(receiverConfig().build(), sink);
        Exception e = assertThrows(Exception.class, () -> receiver.termination().get(5, TimeUnit.SECONDS));
        assertTrue(e.getCause() instanceof IOException, "cause: " + e.getCause());
        assertFalse(receiver.isRunning());
    }

    @Test
    void startFailureReleasesResources() throws Exception {
        RecordingSink sink = new RecordingSink();
        sink.failOnOpen.set(true);
        AudioReceiver receiver = track(new AudioReceiver(receiverConfig().build(), sink));
        assertThrows(IOException.class, receiver::start);
        assertTrue(sink.closed.get());
        assertFalse(receiver.isRunning());
        assertThrows(IllegalStateException.class, receiver::start);
    }

    @Test
    void closeIsIdempotentAndStopsWorkers() throws Exception {
        RecordingSink sink = new RecordingSink();
        AudioReceiver receiver = startReceiver(receiverConfig().build(), sink);
        receiver.close();
        receiver.close();
        assertFalse(receiver.isRunning());
        assertTrue(sink.closed.get());
        receiver.termination().get(1, TimeUnit.SECONDS);
    }

    private ReceiverConfig.Builder receiverConfig() {
        return ReceiverConfig.builder()
                .bindAddress(new InetSocketAddress(LOOPBACK, 0))
                .audio(AUDIO)
                .jitterMs(10);
    }

    private AudioReceiver startReceiver(ReceiverConfig config, AudioSink sink) throws IOException {
        AudioReceiver receiver = track(new AudioReceiver(config, sink));
        receiver.start();
        return receiver;
    }

    private <T extends AutoCloseable> T track(T resource) {
        resources.add(resource);
        return resource;
    }

    private static AudioPacket validPacket(int streamId, int sequence) {
        return new AudioPacket(streamId, sequence, 0, AUDIO.sampleRate(), AUDIO.channels(), 16,
                new byte[AUDIO.payloadBytes()]);
    }

    private static void sendRaw(AudioReceiver receiver, AudioPacket packet) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(PacketCodec.encodedSize(packet));
        PacketCodec.encode(packet, buffer);
        buffer.flip();
        try (DatagramChannel channel = DatagramChannel.open()) {
            channel.send(buffer, receiver.localAddress());
        }
    }

    private static void awaitCondition(BooleanSupplier condition, String description) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Timed out waiting for: " + description);
    }

    /** Produces {@code frames} frames, each tagged with its index, paced at the frame duration. */
    private static final class SequenceSource implements AudioSource {
        private final int frames;
        private final int frameBytes = AUDIO.payloadBytes();
        private int produced;
        private int offsetInFrame;
        private volatile boolean closed;

        SequenceSource(int frames) {
            this.frames = frames;
        }

        @Override
        public void open() {
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (closed || produced >= frames) {
                return -1;
            }
            if (offsetInFrame == 0) {
                try {
                    Thread.sleep(AUDIO.frameDurationMs());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            int count = Math.min(length, frameBytes - offsetInFrame);
            ByteBuffer frame = ByteBuffer.allocate(frameBytes);
            frame.putInt(produced + 1); // never zero, so it is distinguishable from silence
            frame.put(frameBytes - 1, (byte) 0x55);
            System.arraycopy(frame.array(), offsetInFrame, buffer, offset, count);
            offsetInFrame += count;
            if (offsetInFrame == frameBytes) {
                offsetInFrame = 0;
                produced++;
            }
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Records the ids of non-silent frames. Paces itself slightly faster than real time. */
    private static final class RecordingSink implements AudioSink {
        final List<Integer> frameIds = new CopyOnWriteArrayList<>();
        final AtomicBoolean failOnOpen = new AtomicBoolean();
        final AtomicBoolean failOnWrite = new AtomicBoolean();
        final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void open() throws IOException {
            if (failOnOpen.get()) {
                throw new IOException("simulated open failure");
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            if (failOnWrite.get()) {
                throw new IOException("simulated device failure");
            }
            int id = ByteBuffer.wrap(buffer, offset, length).getInt();
            if (id != 0) {
                frameIds.add(id);
            }
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }
}
