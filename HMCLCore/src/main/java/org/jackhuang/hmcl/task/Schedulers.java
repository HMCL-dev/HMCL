// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import javafx.application.Platform;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.MathUtils;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Supplies executors used for task bodies, blocking I/O, and JavaFX callbacks.
public final class Schedulers {

    /// Prevents instantiation.
    private Schedulers() {
    }

    /// Virtual-thread executor factory, absent on runtimes older than Java 21.
    private static final @Nullable Function<String, ExecutorService> NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR;

    static {
        if (Runtime.version().feature() >= 21) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();

                Class<?> vtBuilderCls = Class.forName("java.lang.Thread$Builder$OfVirtual");

                MethodHandle ofVirtualHandle = lookup.findStatic(Thread.class, "ofVirtual", MethodType.methodType(vtBuilderCls));
                MethodHandle setNameHandle = lookup.findVirtual(vtBuilderCls, "name", MethodType.methodType(vtBuilderCls, String.class, long.class));
                MethodHandle toFactoryHandle = lookup.findVirtual(vtBuilderCls, "factory", MethodType.methodType(ThreadFactory.class));
                MethodHandle newThreadPerTaskExecutorFactory = lookup.findStatic(Executors.class, "newThreadPerTaskExecutor", MethodType.methodType(ExecutorService.class, ThreadFactory.class));

                NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR = name -> {
                    try {
                        Object virtualThreadBuilder = ofVirtualHandle.invoke();
                        setNameHandle.invoke(virtualThreadBuilder, name, 1L);
                        ThreadFactory threadFactory = (ThreadFactory) toFactoryHandle.invoke(virtualThreadBuilder);

                        return (ExecutorService) newThreadPerTaskExecutorFactory.invokeExact(threadFactory);
                    } catch (Throwable e) {
                        throw new AssertionError("Unreachable", e);
                    }
                };
            } catch (Throwable e) {
                throw new AssertionError("Unreachable", e);
            }
        } else {
            NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR = null;
        }
    }

    /// Creates an unbounded service that starts a named virtual thread for each submission.
    /// Returns `null` on runtimes older than Java 21.
    public static @Nullable ExecutorService newVirtualThreadPerTaskExecutor(String name) {
        if (NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR == null) {
            return null;
        }

        return new UnboundedExecutorService(NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.apply(name));
    }

    /// This thread pool is suitable for network and local I/O operations.
    ///
    /// For Java 21 or later, all tasks will be dispatched to virtual threads.
    ///
    /// @return Thread pool for I/O operations.
    public static ExecutorService io() {
        return Holder.IO_EXECUTOR;
    }

    /// Executes task bodies on virtual threads when available, or on a private fork/join
    /// pool that can compensate for workers waiting on child tasks.
    static Executor tasks() {
        return Holder.TASK_EXECUTOR;
    }

    /// Dispatches work to the JavaFX application thread.
    public static Executor javafx() {
        return JAVAFX_EXECUTOR;
    }

    /// The shared executor identity used when a JavaFX callback waits for a task.
    private static final Executor JAVAFX_EXECUTOR = Platform::runLater;

    /// Logs the application shutdown request.
    public static void shutdown() {
        LOG.info("Shutting down executor services.");

        // shutdownNow will interrupt all threads.
        // So when we want to close the app, no threads need to be waited for finish.
        // Sometimes it resolves the problem that the app does not exit.
    }

    /// Initializes shared executors on first use.
    private static final class Holder {
        /// Workers used by tasks that perform blocking I/O.
        private static final ExecutorService IO_EXECUTOR = newIoExecutor();

        /// Workers used by task bodies that synchronously coordinate child tasks.
        private static final ExecutorService TASK_EXECUTOR = newTaskExecutor();

        /// Supplies virtual threads or a platform pool with a CPU-based target parallelism.
        private static ExecutorService newTaskExecutor() {
            @Nullable ExecutorService virtualThreads = newVirtualThreadPerTaskExecutor("Task");
            if (virtualThreads != null)
                return virtualThreads;

            int parallelism = MathUtils.clamp(Runtime.getRuntime().availableProcessors() * 4, 4, 64);
            AtomicInteger workerIds = new AtomicInteger();
            // Task submissions are not joined as ForkJoinTasks, so use local FIFO scheduling.
            return new ForkJoinPool(parallelism, pool -> {
                ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName("Task-" + workerIds.incrementAndGet());
                return worker;
            }, null, true);
        }

        /// Creates an executor in which a waiting parent does not occupy a child's only worker.
        private static ExecutorService newIoExecutor() {
            @Nullable ExecutorService virtualThreads = newVirtualThreadPerTaskExecutor("IO");
            if (virtualThreads != null)
                return virtualThreads;
            return new UnboundedExecutorService(Executors.newCachedThreadPool(Lang.counterThreadFactory("IO", true)));
        }
    }

}
