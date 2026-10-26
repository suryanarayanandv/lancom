package com.zoho.lays.lancom.audio;

import java.io.IOException;

/** A blocking source of PCM audio, such as a microphone or loopback device. */
public interface AudioSource extends AutoCloseable {

    /** Acquires the underlying device. Must be called once before {@link #read}. */
    void open() throws IOException;

    /**
     * Reads up to {@code length} bytes, blocking until at least some audio is available.
     *
     * @return bytes read, or {@code -1} once the source has ended or been closed
     */
    int read(byte[] buffer, int offset, int length) throws IOException;

    /** Releases the device. Idempotent and safe to call from any thread, including to unblock {@link #read}. */
    @Override
    void close();
}
