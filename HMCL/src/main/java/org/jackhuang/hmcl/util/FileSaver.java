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

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class FileSaver extends Thread {

    private static final Pair<Path, String> SHUTDOWN = Pair.pair(null, null);

    private static final BlockingQueue<Pair<Path, String>> queue = new LinkedBlockingQueue<>();
    private static final AtomicBoolean running = new AtomicBoolean(false);
    private static final ReentrantLock runningLock = new ReentrantLock();
    private static final AtomicBoolean installedShutdownHook = new AtomicBoolean(false);
    private static volatile boolean shutdown = false;

    private static void doSave(Map<Path, String> map) {
        for (Map.Entry<Path, String> entry : map.entrySet()) {
            saveSync(entry.getKey(), entry.getValue());
        }
    }

    private static void onExit() {
        shutdown();
        runningLock.lock();
        try {
            HashMap<Path, String> map = new HashMap<>();
            for (Pair<Path, String> pair : queue) {
                if (pair != SHUTDOWN)
                    map.put(pair.getKey(), pair.getValue());
            }
            doSave(map);
        } finally {
            runningLock.unlock();
        }
    }

    public static void save(Path file, String content) {
        Objects.requireNonNull(file);
        Objects.requireNonNull(content);

        if (shutdown) {
            saveSync(file, content);
            return;
        }

        queue.add(Pair.pair(file, content));
        if (running.compareAndSet(false, true)) {
            if (runningLock.tryLock()) { // Wait for the previous FileSaver to stop
                try {
                    new FileSaver().start();
                } finally {
                    runningLock.unlock();
                }
            } else {
                // This method is often called on the FX thread; we should avoid blocking the FX thread.
                Schedulers.defaultScheduler().execute(() -> {
                    runningLock.lock();
                    try {
                        new FileSaver().start();
                    } finally {
                        runningLock.unlock();
                    }
                });
            }

            if (installedShutdownHook.compareAndSet(false, true))
                Runtime.getRuntime().addShutdownHook(new Thread(FileSaver::onExit, "SettingsSaverShutdownHook"));
        }
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
        queue.add(SHUTDOWN);
    }

    private FileSaver() {
        super("SettingsSaver");
    }

    private boolean stopped = false;

    private void stopCurrentSaver() {
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
            ArrayList<Pair<Path, String>> buffer = new ArrayList<>();

            while (!stopped) {
                if (shutdown) {
                    stopCurrentSaver();
                } else {
                    Pair<Path, String> head = queue.poll(60, TimeUnit.SECONDS);
                    if (head == null || head == SHUTDOWN) {
                        stopCurrentSaver();
                    } else {
                        map.put(head.getKey(), head.getValue());
                        //noinspection BusyWait
                        Thread.sleep(100); // Waiting for more changes
                    }
                }

                while (queue.drainTo(buffer) > 0) {
                    for (Pair<Path, String> pair : buffer) {
                        if (pair == SHUTDOWN)
                            stopCurrentSaver();
                        else
                            map.put(pair.getKey(), pair.getValue());
                    }
                    buffer.clear();
                }

                doSave(map);
                map.clear();
            }
        } catch (InterruptedException e) {
            throw new AssertionError("This thread cannot be interrupted", e);
        } finally {
            runningLock.unlock();
        }
    }
}
