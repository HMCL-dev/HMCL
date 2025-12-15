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
package org.jackhuang.hmcl.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/// When [#accept(T)] is called, this class invokes the handler on another thread.
/// If [#accept(T)] is called more than one time before the handler starts processing,
/// the handler will only be invoked once, taking the latest argument as its input.
///
/// @author yushijinhun
public final class InvocationDispatcher<T> implements Consumer<T> {

    private static final VarHandle PENDING_ARG_HANDLE;
    static {
        try {
            PENDING_ARG_HANDLE = MethodHandles.lookup()
                    .findVarHandle(InvocationDispatcher.class, "pendingArg", Holder.class);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /// @param executor The executor must dispatch all tasks to a single thread.
    public static <T> InvocationDispatcher<T> runOn(Executor executor, Consumer<T> action) {
        return new InvocationDispatcher<>(executor, action);
    }

    private final Executor executor;
    private final Consumer<T> action;

    /// @see #PENDING_ARG_HANDLE
    @SuppressWarnings("unused")
    private volatile Holder<T> pendingArg;

    private InvocationDispatcher(Executor executor, Consumer<T> action) {
        this.executor = executor;
        this.action = action;
    }

    @Override
    public void accept(T t) {
        if (PENDING_ARG_HANDLE.getAndSet(this, new Holder<>(t)) == null) {
            executor.execute(() -> {
                @SuppressWarnings("unchecked")
                var holder = (Holder<T>) PENDING_ARG_HANDLE.getAndSet(this, (Holder<T>) null);

                // If the executor supports multiple underlying threads,
                // we need to add synchronization, but for now we can omit it :)
                // synchronized (InvocationDispatcher.this)
                action.accept(holder.value);
            });
        }
    }
}
