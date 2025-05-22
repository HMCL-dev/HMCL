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
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class SettingsSaver extends Thread {

    private static final Pair<Path, String> SHUTDOWN = Pair.pair(null, null);

    private static final BlockingQueue<Pair<Path, String>> queue = new LinkedBlockingQueue<>();

    private static volatile boolean running = false;
    private static final ReentrantLock runningLock = new ReentrantLock();
    private static boolean installedShutdownHook;

    private static void doSave(Map<Path, String> map) {
        for (Map.Entry<Path, String> entry : map.entrySet()) {
            Path file = entry.getKey();
            LOG.info("Saving settings: " + file);
            try {
                FileUtils.saveSafely(file, entry.getValue());
            } catch (Throwable e) {
                LOG.warning("Failed to save " + file, e);
            }
        }
    }

    private static void onExit() {
        shutdown();
        runningLock.lock();
        try {
            IdentityHashMap<Path, String> map = new IdentityHashMap<>();
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

        queue.add(Pair.pair(file, content));
        if (!running) {
            Schedulers.defaultScheduler().execute(() -> {
                runningLock.lock();
                try {
                    if (!running) {
                        SettingsSaver saver = new SettingsSaver();
                        saver.start();
                        running = true;
                    }

                    if (!installedShutdownHook) {
                        installedShutdownHook = true;
                        Runtime.getRuntime().addShutdownHook(new Thread(SettingsSaver::onExit, "SettingsSaverShutdownHook"));
                    }
                } finally {
                    runningLock.unlock();
                }
            });
        }
    }

    public static void shutdown() {
        queue.add(SHUTDOWN);
    }

    private SettingsSaver() {
        super("SettingsSaver");
    }

    @Override
    public void run() {
        runningLock.lock();
        try {
            IdentityHashMap<Path, String> map = new IdentityHashMap<>();
            ArrayList<Pair<Path, String>> buffer = new ArrayList<>();

            while (running) {
                Pair<Path, String> head = queue.poll(10, TimeUnit.SECONDS);
                if (head == null || head == SHUTDOWN) {
                    running = false;
                } else {
                    map.put(head.getKey(), head.getValue());
                    //noinspection BusyWait
                    Thread.sleep(100); // Waiting for more changes
                }

                while (queue.drainTo(buffer) > 0) {
                    for (Pair<Path, String> pair : buffer) {
                        if (pair == SHUTDOWN)
                            running = false;
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
