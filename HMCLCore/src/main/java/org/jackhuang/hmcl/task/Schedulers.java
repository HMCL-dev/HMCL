/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 *
 * @author huangyuhui
 */
public final class Schedulers {

    private Schedulers() {
    }

    private static volatile ThreadPoolExecutor CACHED_EXECUTOR;

    public static synchronized ThreadPoolExecutor newThread() {
        if (CACHED_EXECUTOR == null)
            CACHED_EXECUTOR = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                    60, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory());

        return CACHED_EXECUTOR;
    }

    private static volatile ThreadPoolExecutor IO_EXECUTOR;

    public static synchronized ThreadPoolExecutor io() {
        if (IO_EXECUTOR == null)
            IO_EXECUTOR = new ThreadPoolExecutor(6, 6,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    runnable -> {
                        Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                        thread.setDaemon(true);
                        return thread;
                    });

        return IO_EXECUTOR;
    }

    private static volatile ExecutorService SINGLE_EXECUTOR;

    public static synchronized ExecutorService computation() {
        if (SINGLE_EXECUTOR == null)
            SINGLE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setDaemon(true);
                return thread;
            });

        return SINGLE_EXECUTOR;
    }

    public static Executor javafx() {
        return Platform::runLater;
    }

    public static Executor swing() {
        return SwingUtilities::invokeLater;
    }

    public static Executor defaultScheduler() {
        return newThread();
    }

    public static synchronized void shutdown() {
        Logging.LOG.info("Shutting down executor services.");

        if (CACHED_EXECUTOR != null)
            CACHED_EXECUTOR.shutdownNow();

        if (IO_EXECUTOR != null)
            IO_EXECUTOR.shutdownNow();

        if (SINGLE_EXECUTOR != null)
            SINGLE_EXECUTOR.shutdownNow();
    }

    public static Future<?> schedule(Executor executor, Runnable command) {
        if (executor instanceof ExecutorService) {
            return ((ExecutorService) executor).submit(command);
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> wrapper = new AtomicReference<>();

        executor.execute(() -> {
            try {
                command.run();
            } catch (Exception e) {
                wrapper.set(e);
            } finally {
                latch.countDown();
            }
            Thread.interrupted(); // clear the `interrupted` flag to prevent from interrupting EventDispatch thread.
        });

        return new Future<Void>() {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return latch.getCount() == 0;
            }

            private Void getImpl() throws ExecutionException {
                Exception e = wrapper.get();
                if (e != null)
                    throw new ExecutionException(e);
                return null;
            }

            @Override
            public Void get() throws InterruptedException, ExecutionException {
                latch.await();
                return getImpl();
            }

            @Override
            public Void get(long timeout, @NotNull TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                if (!latch.await(timeout, unit))
                    throw new TimeoutException();
                return getImpl();
            }
        };
    }
}
