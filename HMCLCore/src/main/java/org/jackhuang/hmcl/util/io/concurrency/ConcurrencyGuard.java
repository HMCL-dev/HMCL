package org.jackhuang.hmcl.util.io.concurrency;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConcurrencyGuard {
    private final Semaphore semaphore;

    public ConcurrencyGuard(int permits) {
        this.semaphore = new Semaphore(permits);
    }

    public Token acquire() {
        semaphore.acquireUninterruptibly();
        return new Token();
    }

    public final class Token implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        private Token() {
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
