/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public final class FileSaver extends Thread {

    private static final BlockingQueue<Action> queue = new LinkedBlockingQueue<>();
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final ReentrantLock runningLock = new ReentrantLock();
    private static volatile boolean shutdown = false;

    private static void addAction(Action action) {
        queue.add(action);
        if (running.compareAndSet(false, true)) {
            new FileSaver().start();
        }
    }

    private static void doSave(Map<Path, String> map) {
        for (Map.Entry<Path, String> entry : map.entrySet()) {
            saveSync(entry.getKey(), entry.getValue());
        }
    }

    public static void save(Path file, String content) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(content);

        ShutdownHook.ensureInstalled();

        addAction(new DoSave(file, content));
    }

    public static void saveSync(Path file, String content) {
        LOG.info("Saving file " + file);
        try {
            FileUtils.saveSafely(file, content);
        } catch (Throwable e) {
            LOG.warning("Failed to save " + file, e);
        }
    }

    public static void shutdown() {
        shutdown = true;
        queue.add(Shutdown.INSTANCE);
    }

    /// Wait for all saves to complete.
    ///
    /// This method is not ensure all saves after [#shutdown] has been completed.
    public static void waitForAllSaves() throws InterruptedException {
        assert !shutdown;
        Wait wait = new Wait();
        addAction(wait);
        wait.await();
    }

    private FileSaver() {
        super("FileSaver");
    }

    private boolean stopped = false;

    private void stopCurrentSaver() {
        // Ensure that each saver calls `running.set(false)` at most once
        if (!stopped) {
            stopped = true;
            running.set(false);
        }
    }

    @Override
    public void run() {
        runningLock.lock();
        try {
            HashMap<Path, String> map = new HashMap<>();
            ArrayList<Action> buffer = new ArrayList<>();
            ArrayList<Wait> waits = new ArrayList<>();

            while (!stopped) {
                if (shutdown) {
                    stopCurrentSaver();
                } else {
                    Action head = queue.poll(30, TimeUnit.SECONDS);
                    if (head instanceof DoSave save) {
                        map.put(save.file(), save.content());
                        //noinspection BusyWait
                        Thread.sleep(200); // Waiting for more changes
                    } else if (head instanceof Wait wait) {
                        waits.add(wait);
                    } else if (head == null || head instanceof Shutdown) {
                        // Shutdown or timeout
                        stopCurrentSaver();
                    }
                }

                while (queue.drainTo(buffer) > 0) {
                    for (Action action : buffer) {
                        if (action instanceof DoSave save) {
                            map.put(save.file(), save.content());
                        } else if (action instanceof Wait wait) {
                            waits.add(wait);
                        } else if (action instanceof Shutdown) {
                            stopCurrentSaver();
                        }
                    }
                    buffer.clear();
                }

                doSave(map);
                map.clear();

                for (Wait wait : waits) {
                    wait.countDown();
                }
                waits.clear();
            }
        } catch (InterruptedException e) {
            throw new AssertionError("This thread cannot be interrupted", e);
        } finally {
            runningLock.unlock();
        }
    }

    private sealed interface Action {
    }

    private record DoSave(Path file, String content) implements Action {
    }

    private static final class Wait implements Action {
        private final CountDownLatch latch = new CountDownLatch(1);

        public void await() throws InterruptedException {
            latch.await();
        }

        public void countDown() {
            latch.countDown();
        }
    }

    private enum Shutdown implements Action {
        INSTANCE
    }

    private static final class ShutdownHook extends Thread {

        static {
            Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        }

        static void ensureInstalled() {
            // Ensure the shutdown hook is installed
        }

        @Override
        public void run() {
            shutdown();
            runningLock.lock();
            try {
                HashMap<Path, String> map = new HashMap<>();
                for (Action action : queue) {
                    if (action instanceof DoSave save) {
                        map.put(save.file(), save.content());
                    }
                }
                doSave(map);
            } finally {
                runningLock.unlock();
            }
        }
    }
}
