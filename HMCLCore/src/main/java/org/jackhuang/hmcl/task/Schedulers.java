/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import org.jackhuang.hmcl.util.Logging;

import javax.swing.*;
import java.util.concurrent.*;

import static org.jackhuang.hmcl.util.Lang.threadPool;

/**
 *
 * @author huangyuhui
 */
public final class Schedulers {

    private Schedulers() {
    }

    private static volatile ExecutorService IO_EXECUTOR;

    /**
     * Get singleton instance of the thread pool for I/O operations,
     * usually for reading files from disk, or Internet connections.
     *
     * This thread pool has no more than 4 threads, and number of threads will get
     * reduced if concurrency is less than thread number.
     *
     * @return Thread pool for I/O operations.
     */
    public static ExecutorService io() {
        if (IO_EXECUTOR == null) {
            synchronized (Schedulers.class) {
                if (IO_EXECUTOR == null) {
                    IO_EXECUTOR = threadPool("IO", true, 4, 10, TimeUnit.SECONDS);
                }
            }
        }

        return IO_EXECUTOR;
    }

    public static Executor javafx() {
        return Platform::runLater;
    }

    public static Executor swing() {
        return SwingUtilities::invokeLater;
    }

    public static Executor defaultScheduler() {
        return ForkJoinPool.commonPool();
    }

    public static synchronized void shutdown() {
        Logging.LOG.info("Shutting down executor services.");

        // shutdownNow will interrupt all threads.
        // So when we want to close the app, no threads need to be waited for finish.
        // Sometimes it resolves the problem that the app does not exit.

        if (IO_EXECUTOR != null)
            IO_EXECUTOR.shutdownNow();
    }

}
