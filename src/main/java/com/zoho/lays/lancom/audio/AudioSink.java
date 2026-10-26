package com.zoho.lays.lancom.audio;

import java.io.IOException;

/**
 * A blocking PCM audio output, such as speakers or Bluetooth headphones. Implementations should block in
 * {@link #write} while their buffer is full; that back-pressure is what paces playback to the device clock.
 */
public interface AudioSink extends AutoCloseable {

    /** Acquires the underlying device. Must be called once before {@link #write}. */
    void open() throws IOException;

    /** Writes exactly {@code length} bytes, blocking as needed. */
    void write(byte[] buffer, int offset, int length) throws IOException;

    /** Releases the device. Idempotent and safe to call from any thread, including to unblock {@link #write}. */
    @Override
    void close();
}
