package com.zoho.lays.lancom.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static com.zoho.lays.lancom.audio.PcmChannelConverterTest.pcm;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelConvertingSourceTest {

    @Test
    void convertsMonoDeviceToStereoStream() throws IOException {
        ChannelConvertingSource source = new ChannelConvertingSource(new ChunkedSource(pcm(1, 2, 3), 64), 1, 2);
        assertArrayEquals(pcm(1, 1, 2, 2, 3, 3), readAll(source, 64));
    }

    @Test
    void convertsStereoDeviceToMonoStream() throws IOException {
        ChannelConvertingSource source = new ChannelConvertingSource(new ChunkedSource(pcm(10, 20, -10, -20), 64), 2, 1);
        assertArrayEquals(pcm(15, -15), readAll(source, 64));
    }

    @Test
    void handlesDelegateReturningSingleBytes() throws IOException {
        // Device returns one byte at a time: frames are split across reads and must be reassembled.
        ChannelConvertingSource source = new ChannelConvertingSource(new ChunkedSource(pcm(7, -7, 300), 1), 1, 2);
        assertArrayEquals(pcm(7, 7, -7, -7, 300, 300), readAll(source, 64));
    }

    @Test
    void handlesCallerRequestingLessThanOneFrame() throws IOException {
        // Caller reads 1 or 3 bytes at a time: a converted frame must be handed out across reads.
        ChannelConvertingSource oneByte = new ChannelConvertingSource(new ChunkedSource(pcm(1, 2), 64), 1, 2);
        assertArrayEquals(pcm(1, 1, 2, 2), readAll(oneByte, 1));
        ChannelConvertingSource threeBytes = new ChannelConvertingSource(new ChunkedSource(pcm(1, 2, 3), 64), 1, 2);
        assertArrayEquals(pcm(1, 1, 2, 2, 3, 3), readAll(threeBytes, 3));
    }

    @Test
    void randomChunkingMatchesDirectConversion() throws IOException {
        Random random = new Random(42);
        for (int trial = 0; trial < 200; trial++) {
            int frames = random.nextInt(300);
            byte[] mono = new byte[frames * 2];
            random.nextBytes(mono);
            byte[] expected = new byte[frames * 4];
            PcmChannelConverter.convert(mono, 0, frames, 1, expected, 0, 2);

            ChannelConvertingSource source = new ChannelConvertingSource(
                    new ChunkedSource(mono, 1 + random.nextInt(50)), 1, 2);
            assertArrayEquals(expected, readAll(source, 1 + random.nextInt(100)), "trial " + trial);
        }
    }

    @Test
    void returnsEndOfStreamAndDiscardsTrailingPartialFrame() throws IOException {
        byte[] withPartial = Arrays.copyOf(pcm(5), 3); // one frame plus a dangling byte
        ChannelConvertingSource source = new ChannelConvertingSource(new ChunkedSource(withPartial, 64), 1, 2);
        byte[] buffer = new byte[16];
        assertEquals(4, source.read(buffer, 0, 16));
        assertEquals(-1, source.read(buffer, 0, 16));
    }

    @Test
    void zeroLengthReadReturnsZero() throws IOException {
        ChannelConvertingSource source = new ChannelConvertingSource(new ChunkedSource(pcm(1), 64), 1, 2);
        assertEquals(0, source.read(new byte[4], 0, 0));
    }

    @Test
    void delegatesLifecycle() throws IOException {
        ChunkedSource delegate = new ChunkedSource(new byte[0], 1);
        ChannelConvertingSource source = new ChannelConvertingSource(delegate, 1, 2);
        source.open();
        source.close();
        assertTrue(delegate.opened);
        assertTrue(delegate.closed);
    }

    private static byte[] readAll(AudioSource source, int chunk) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[chunk + 3];
        while (true) {
            int read = source.read(buffer, 3, chunk); // non-zero offset on purpose
            if (read < 0) {
                return out.toByteArray();
            }
            assertTrue(read > 0 && read <= chunk, "read " + read + " for chunk " + chunk);
            out.write(buffer, 3, read);
        }
    }

    /** Returns its data in chunks of at most {@code chunk} bytes, then end of stream. */
    private static final class ChunkedSource implements AudioSource {
        private final byte[] data;
        private final int chunk;
        private int position;
        boolean opened;
        boolean closed;

        ChunkedSource(byte[] data, int chunk) {
            this.data = data;
            this.chunk = chunk;
        }

        @Override
        public void open() {
            opened = true;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) {
            if (position >= data.length) {
                return -1;
            }
            int count = Math.min(Math.min(chunk, length), data.length - position);
            System.arraycopy(data, position, buffer, offset, count);
            position += count;
            return count;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
