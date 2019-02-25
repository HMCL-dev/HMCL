/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import org.jackhuang.hmcl.util.function.ExceptionalRunnable;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @author huangyuhui
 */
class SchedulerImpl extends Scheduler {

    private final Executor executor;

    public SchedulerImpl(Executor executor) {
        this.executor = executor;
    }

    @Override
    public Future<?> schedule(ExceptionalRunnable<?> block) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> wrapper = new AtomicReference<>();

        executor.execute(() -> {
            try {
                block.run();
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
