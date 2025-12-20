package org.jackhuang.hmcl.util.io.concurrency;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ConcurrencyGuard {
    private final Semaphore semaphore;

    public ConcurrencyGuard(Semaphore semaphore) {
        this.semaphore = semaphore;
    }

    public Token acquire() {
        semaphore.acquireUninterruptibly();
        return new Token(semaphore);
    }

    public static final class Token implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean closed = new AtomicBoolean();

        public Token(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }
}
