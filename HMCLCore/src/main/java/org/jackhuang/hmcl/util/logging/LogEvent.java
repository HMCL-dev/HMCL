package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;

/**
 * @author Glavo
 */
abstract class LogEvent {
    static final class DoLog extends LogEvent {
        final Instant instant;
        final String caller;
        final String level;
        final String message;
        final Throwable exception;

        DoLog(Instant instant, String caller, String level, String message, Throwable exception) {
            this.instant = instant;
            this.caller = caller;
            this.level = level;
            this.message = message;
            this.exception = exception;
        }
    }

    static final class ExportLog extends LogEvent {
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
}
