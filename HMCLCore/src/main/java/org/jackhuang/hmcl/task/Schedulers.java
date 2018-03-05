/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.task;

import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Logging;

import java.util.concurrent.*;

/**
 *
 * @author huangyuhui
 */
public final class Schedulers {

    private Schedulers() {
    }

    private static volatile ExecutorService CACHED_EXECUTOR;

    private static synchronized ExecutorService getCachedExecutorService() {
        if (CACHED_EXECUTOR == null)
            CACHED_EXECUTOR = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
                    60, TimeUnit.SECONDS, new SynchronousQueue<>(), Executors.defaultThreadFactory());

        return CACHED_EXECUTOR;
    }

    private static volatile ExecutorService IO_EXECUTOR;

    private static synchronized ExecutorService getIOExecutorService() {
        if (IO_EXECUTOR == null)
            IO_EXECUTOR = Executors.newFixedThreadPool(6, runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setDaemon(true);
                return thread;
            });

        return IO_EXECUTOR;
    }

    private static volatile ExecutorService SINGLE_EXECUTOR;

    private static synchronized ExecutorService getSingleExecutorService() {
        if (SINGLE_EXECUTOR == null)
            SINGLE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = Executors.defaultThreadFactory().newThread(runnable);
                thread.setDaemon(true);
                return thread;
            });

        return SINGLE_EXECUTOR;
    }

    private static final Scheduler IMMEDIATE = new SchedulerImpl(Runnable::run);

    public static Scheduler immediate() {
        return IMMEDIATE;
    }

    private static Scheduler NEW_THREAD;

    public static synchronized Scheduler newThread() {
        if (NEW_THREAD == null)
            NEW_THREAD = new SchedulerExecutorService(getCachedExecutorService());
        return NEW_THREAD;
    }

    private static Scheduler IO;

    public static synchronized Scheduler io() {
        if (IO == null)
            IO = new SchedulerExecutorService(getIOExecutorService());
        return IO;
    }

    private static Scheduler COMPUTATION;

    public static synchronized Scheduler computation() {
        if (COMPUTATION == null)
            COMPUTATION = new SchedulerExecutorService(getSingleExecutorService());
        return COMPUTATION;
    }

    private static final Scheduler JAVAFX = new SchedulerImpl(javafx.application.Platform::runLater);

    public static Scheduler javafx() {
        return JAVAFX;
    }

    private static final Scheduler SWING = new SchedulerImpl(javax.swing.SwingUtilities::invokeLater);

    public static Scheduler swing() {
        return SWING;
    }

    public static synchronized Scheduler defaultScheduler() {
        return newThread();
    }

    static final Scheduler NONE = new SchedulerImpl(Constants.emptyConsumer());

    public static synchronized void shutdown() {
        Logging.LOG.info("Shutting down executor services.");

        if (CACHED_EXECUTOR != null)
            CACHED_EXECUTOR.shutdown();

        if (IO_EXECUTOR != null)
            IO_EXECUTOR.shutdown();

        if (SINGLE_EXECUTOR != null)
            SINGLE_EXECUTOR.shutdown();
    }
}
