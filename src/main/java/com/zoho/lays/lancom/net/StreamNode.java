package com.zoho.lays.lancom.net;

import com.zoho.lays.lancom.metrics.StreamStats;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lifecycle shared by streaming nodes: start once, run worker threads, shut down exactly once on
 * {@link #close()} or on the first worker failure. {@link #termination()} completes normally after a clean
 * close and exceptionally after a failure.
 */
public abstract class StreamNode implements AutoCloseable {
    private static final Logger LOG = Logger.getLogger(StreamNode.class.getName());
    private static final long WORKER_JOIN_TIMEOUT_MS = 2_000;

    /** Worker body that may throw. */
    @FunctionalInterface
    protected interface Task {
        void run() throws Exception;
    }

    protected final StreamStats stats = new StreamStats();

    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> termination = new CompletableFuture<>();
    private final List<Thread> workers = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    /**
     * Acquires resources and starts streaming. On failure all resources are released, the node is closed and
     * the exception is rethrown.
     *
     * @throws IllegalStateException if already started or closed
     */
    public abstract void start() throws IOException;

    /** Releases sockets and devices. Called exactly once, while holding this node's monitor. */
    protected abstract void releaseResources();

    public final StreamStats stats() {
        return stats;
    }

    public final boolean isRunning() {
        return running;
    }

    public final CompletableFuture<Void> termination() {
        return termination.copy();
    }

    /** Must be the first call in {@link #start()}. */
    protected final void beginStart() {
        if (closed.get()) {
            throw new IllegalStateException(getClass().getSimpleName() + " is closed");
        }
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException(getClass().getSimpleName() + " already started");
        }
        running = true;
    }

    protected final void startWorker(String name, Task task) {
        Thread thread = new Thread(() -> {
            try {
                task.run();
            } catch (Throwable e) {
                if (running) {
                    fail(e);
                } else {
                    LOG.log(Level.FINE, "Worker " + name + " stopped during shutdown", e);
                }
            }
        }, name);
        workers.add(thread);
        thread.start();
    }

    protected final void fail(Throwable cause) {
        if (shutdown()) {
            LOG.log(Level.SEVERE, getClass().getSimpleName() + " failed: " + cause.getMessage(), cause);
            termination.completeExceptionally(cause);
        }
    }

    @Override
    public final void close() {
        if (shutdown()) {
            termination.complete(null);
        }
    }

    private boolean shutdown() {
        if (!closed.compareAndSet(false, true)) {
            return false;
        }
        running = false;
        synchronized (this) {
            try {
                releaseResources();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Error releasing resources", e);
            }
        }
        Thread current = Thread.currentThread();
        for (Thread worker : workers) {
            if (worker == current) {
                continue;
            }
            try {
                worker.join(WORKER_JOIN_TIMEOUT_MS);
                if (worker.isAlive()) {
                    LOG.warning(() -> "Worker " + worker.getName() + " did not stop within "
                            + WORKER_JOIN_TIMEOUT_MS + " ms");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return true;
    }

    protected static void closeQuietly(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception e) {
            LOG.log(Level.FINE, "Ignoring error while closing " + resource, e);
        }
    }
}
