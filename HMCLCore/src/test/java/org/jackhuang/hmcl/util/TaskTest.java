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

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TaskTest {
    /**
     * TaskExecutor will not catch error and will be thrown to global handler.
     */
    @Test
    public void expectErrorUncaught() {
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> throwable.set(e));
        assertFalse(Task.composeAsync(() -> Task.allOf(
                Task.allOf(Task.runAsync(() -> {
                    throw new Error();
                }))
        )).whenComplete(Assertions::assertNull).test());

        assertInstanceOf(Error.class, throwable.get(), "Error has not been thrown to uncaught exception handler");
    }

    /**
     *
     */
    @Test
    public void testWhenComplete() {
        boolean result = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).whenComplete(exception -> {
            assertInstanceOf(IllegalStateException.class, exception);
        }).test();

        assertFalse(result, "Task should fail at this case");
    }

    @Test
    public void testWithCompose() {
        AtomicBoolean bool = new AtomicBoolean();
        boolean success = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).withRunAsync(() -> {
            bool.set(true);
        }).test();

        assertTrue(success, "Task should success because withRunAsync will ignore previous exception");
        assertTrue(bool.get(), "withRunAsync should be executed");
    }

    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testThenAccept() {
        AtomicBoolean flag = new AtomicBoolean();
        boolean result = Task.supplyAsync(JavaVersion::fromCurrentEnvironment)
                .thenAcceptAsync(Schedulers.io(), javaVersion -> {
                    flag.set(true);
                    assertEquals(javaVersion, JavaVersion.fromCurrentEnvironment());
                })
                .test();

        assertTrue(result, "Task does not succeed");
        assertTrue(flag.get(), "ThenAccept has not been executed");
    }

    @Test
    public void testCancellation() throws InterruptedException {
        AtomicBoolean flag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        Task<?> task = Task.runAsync(() -> {
            latch.countDown();
            Thread.sleep(200);
            // default executor cannot interrupt task.
            flag.getAndSet(true);
        }).thenRunAsync(() -> {
            System.out.println("No way!");
            Thread.sleep(200);
            fail("Cannot reach here");
        });
        TaskExecutor executor = task.executor();
        Lang.thread(() -> {
            try {
                latch.await();
                System.out.println("Main thread start waiting");
                Thread.sleep(100);
                System.out.println("Cancel");
                executor.cancel();
            } catch (InterruptedException e) {
                fail(e);
            }
        });
        assertFalse(executor.test(), "Task should fail because we have cancelled it");
        Thread.sleep(3000);

        assertInstanceOf(CancellationException.class, executor.getException(), "CancellationException should not be recorded.");
        assertInstanceOf(CancellationException.class, task.getException(), "CancellationException should not be recorded.");
        assertTrue(flag.get(), "Thread.sleep cannot be interrupted");
    }

    @Test
    public void testCompletableFutureCancellation() throws Throwable {
        AtomicBoolean flag = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
            latch.countDown();
            System.out.println("Sleep");
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            // default executor cannot interrupt task.
            flag.getAndSet(true);
            System.out.println("End");
        }).thenComposeAsync(non -> {
            System.out.println("compose");
            return CompletableFuture.allOf(CompletableFuture.runAsync(() -> {
                System.out.println("No way!");
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                fail("Cannot reach here");
            }));
        });
        Lang.thread(() -> {
            try {
                latch.await();
                System.out.println("Main thread start waiting");
                Thread.sleep(100);
                System.out.println("Cancel");
                task.cancel(true);
            } catch (InterruptedException e) {
                fail(e);
            }
        });
        System.out.println("Start");
        try {
            task.get();
        } catch (CancellationException e) {
            System.out.println("Successfully cancelled");
        }
        //Assert.assertFalse("Task should fail because we have cancelled it", );
        Thread.sleep(4000);
        //Assert.assertNull("CancellationException should not be recorded.", executor.getException());
        //Assert.assertTrue("Thread.sleep cannot be interrupted", flag.get());
    }

    public void testRejectedExecutionException() {
        Schedulers.defaultScheduler();
        Schedulers.shutdown();

        Task<?> task = Task.runAsync(() -> {
            Thread.sleep(1000);
        });

        boolean result = task.test();

        assertFalse(result, "Task should fail since ExecutorService is shut down and RejectedExecutionException should be thrown");
        assertInstanceOf(RejectedExecutionException.class, task.getException(), "RejectedExecutionException should be recorded");
    }
}
