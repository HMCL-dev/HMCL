package org.jackhuang.hmcl.util.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * @author Glavo
 */
abstract class LogEvent {
    static final class DoLog extends LogEvent {
        final long time;
        final String caller;
        final Level level;
        final String message;
        final Throwable exception;

        DoLog(long time, String caller, Level level, String message, Throwable exception) {
            this.time = time;
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

    static final class Shutdown extends LogEvent {
    }
}
