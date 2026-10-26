package com.zoho.lays.lancom.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static com.zoho.lays.lancom.audio.PcmChannelConverterTest.pcm;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelConvertingSinkTest {

    @Test
    void convertsMonoStreamToStereoDevice() throws IOException {
        CollectingSink device = new CollectingSink();
        ChannelConvertingSink sink = new ChannelConvertingSink(device, 1, 2);
        byte[] buffer = new byte[2 + 4];
        System.arraycopy(pcm(9, -9), 0, buffer, 2, 4);
        sink.write(buffer, 2, 4);
        sink.write(pcm(1), 0, 2);
        assertArrayEquals(pcm(9, 9, -9, -9, 1, 1), device.bytes.toByteArray());
    }

    @Test
    void convertsStereoStreamToMonoDevice() throws IOException {
        CollectingSink device = new CollectingSink();
        new ChannelConvertingSink(device, 2, 1).write(pcm(4, 8, -4, -8), 0, 8);
        assertArrayEquals(pcm(6, -6), device.bytes.toByteArray());
    }

    @Test
    void rejectsPartialFrames() {
        ChannelConvertingSink sink = new ChannelConvertingSink(new CollectingSink(), 2, 1);
        assertThrows(IllegalArgumentException.class, () -> sink.write(new byte[6], 0, 6));
    }

    @Test
    void delegatesLifecycle() throws IOException {
        CollectingSink device = new CollectingSink();
        ChannelConvertingSink sink = new ChannelConvertingSink(device, 1, 2);
        sink.open();
        sink.close();
        assertTrue(device.opened);
        assertTrue(device.closed);
    }

    private static final class CollectingSink implements AudioSink {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean opened;
        boolean closed;

        @Override
        public void open() {
            opened = true;
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            bytes.write(buffer, offset, length);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
