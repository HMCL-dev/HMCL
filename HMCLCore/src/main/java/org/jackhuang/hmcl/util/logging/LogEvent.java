package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * @author Glavo
 */
sealed interface LogEvent {
    record DoLog(long time,
                 String caller,
                 System.Logger.Level level,
                 String message,
                 Throwable exception
    ) implements LogEvent {
    }

    final class ExportLog implements LogEvent {
        final CountDownLatch latch = new CountDownLatch(1);

        final OutputStream output;
        IOException exception;

        ExportLog(OutputStream output) {
            this.output = output;
        }

        void await() throws InterruptedException {
            latch.await();
        }
    }

    final class Shutdown implements LogEvent {
    }
}
