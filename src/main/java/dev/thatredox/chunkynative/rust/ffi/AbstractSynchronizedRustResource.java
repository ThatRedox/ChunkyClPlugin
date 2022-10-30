package dev.thatredox.chunkynative.rust.ffi;

import dev.thatredox.chunkynative.util.NativeCleaner;

import java.util.ConcurrentModificationException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/**
 * A generic non-threadsafe Rust resource. Synchronization is provided by Java.
 */
public class AbstractSynchronizedRustResource implements AutoCloseable {
    private final AtomicLong refCount = new AtomicLong(0);
    private NativeCleaner.Cleaner cleaner = null;
    private long address = 0;

    protected void init(long address, LongConsumer drop) {
        this.cleaner = NativeCleaner.INSTANCE.register(this, () -> drop.accept(address));
        this.address = address;
    }

    public synchronized AddressGuard getAddress() {
        if (refCount.get() == -1 || address == 0) {
            throw new NullPointerException("Invalid native reference.");
        }
        return new AddressGuard(this);
    }

    @Override
    public synchronized void close() throws RuntimeException {
        if (!refCount.compareAndSet(0, -1)) {
            throw new ConcurrentModificationException("Attempted to drop live native reference.");
        }
        cleaner.clean();
    }

    /**
     * This "protects" a native memory address. The memory address is guaranteed to be valid while it is not closed.
     */
    public static class AddressGuard implements AutoCloseable {
        private final AbstractSynchronizedRustResource parent;
        public final long address;

        protected AddressGuard(AbstractSynchronizedRustResource parent) {
            parent.refCount.incrementAndGet();
            this.parent = parent;
            this.address = parent.address;
        }

        @Override
        public void close() {
            parent.refCount.decrementAndGet();
        }
    }
}
