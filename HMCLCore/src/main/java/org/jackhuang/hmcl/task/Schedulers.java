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
import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author huangyuhui
public final class Schedulers {

    private Schedulers() {
    }

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

    /// @return Returns null if the Java version is below 21, otherwise always returns a non-null value.
    public static ExecutorService newVirtualThreadPerTaskExecutor(String name) {
        if (NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR == null) {
            return null;
        }

        return NEW_VIRTUAL_THREAD_PER_TASK_EXECUTOR.apply(name);
    }

    /// This thread pool is suitable for network and local I/O operations.
    ///
    /// For Java 21 or later, all tasks will be dispatched to virtual threads.
    ///
    /// @return Thread pool for I/O operations.
    public static ExecutorService io() {
        return Holder.IO_EXECUTOR;
    }

    public static Executor javafx() {
        return Platform::runLater;
    }

    /// Default thread pool, equivalent to [ForkJoinPool#commonPool()].
    ///
    /// It is recommended to perform computation tasks on this thread pool. For I/O operations, please use [#io()].
    public static Executor defaultScheduler() {
        return ForkJoinPool.commonPool();
    }

    public static void shutdown() {
        LOG.info("Shutting down executor services.");

        // shutdownNow will interrupt all threads.
        // So when we want to close the app, no threads need to be waited for finish.
        // Sometimes it resolves the problem that the app does not exit.
    }

    private static final class Holder {
        private static final ExecutorService IO_EXECUTOR;

        static {
            //noinspection resource
            ExecutorService vtExecutor = newVirtualThreadPerTaskExecutor("IO");
            IO_EXECUTOR = vtExecutor != null
                    ? vtExecutor
                    : Executors.newCachedThreadPool(Lang.counterThreadFactory("IO", true));
        }
    }

}
