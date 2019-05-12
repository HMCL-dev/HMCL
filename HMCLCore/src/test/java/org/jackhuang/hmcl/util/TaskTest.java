package org.jackhuang.hmcl.util;

import javafx.embed.swing.JFXPanel;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class TaskTest {
    /**
     * TaskExecutor will not catch error and will be thrown to global handler.
     */
    @Test
    public void expectErrorUncaught() {
        AtomicReference<Throwable> throwable = new AtomicReference<>();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> throwable.set(e));
        Assert.assertFalse(Task.composeAsync(() -> Task.allOf(
                Task.allOf(Task.runAsync(() -> {
                    throw new Error();
                }))
        )).whenComplete(Assert::assertNull).test());

        Assert.assertTrue("Error has not been thrown to uncaught exception handler", throwable.get() instanceof Error);
    }

    /**
     *
     */
    @Test
    public void testWhenComplete() {
        boolean result = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).whenComplete(exception -> {
            Assert.assertTrue(exception instanceof IllegalStateException);
        }).test();

        Assert.assertFalse("Task should fail at this case", result);
    }

    @Test
    public void testWithCompose() {
        AtomicBoolean bool = new AtomicBoolean();
        boolean success = Task.supplyAsync(() -> {
            throw new IllegalStateException();
        }).withRunAsync(() -> {
            bool.set(true);
        }).test();

        Assert.assertTrue("Task should success because withRunAsync will ignore previous exception", success);
        Assert.assertTrue("withRunAsync should be executed", bool.get());
    }

    @Test
    public void testThenAccept() {
        new JFXPanel(); // init JavaFX Toolkit
        AtomicBoolean flag = new AtomicBoolean();
        boolean result = Task.supplyAsync(JavaVersion::fromCurrentEnvironment)
                .thenAcceptAsync(Schedulers.javafx(), javaVersion -> {
                    flag.set(true);
                    Assert.assertEquals(javaVersion, JavaVersion.fromCurrentEnvironment());
                })
                .test();

        Assert.assertTrue("Task does not succeed", result);
        Assert.assertTrue("ThenAccept has not been executed", flag.get());
    }

    @Test
    public void testCancellation() {
        Task<?> task = Task.runAsync(() -> Thread.sleep(200));
        TaskExecutor executor = task.executor();
        Lang.thread(() -> {
            try {
                Thread.sleep(100);
                executor.cancel();
            } catch (InterruptedException e) {
                Assume.assumeNoException(e);
            }
        });
        Assert.assertFalse("Task should fail because we have cancelled it", executor.test());
        Assert.assertNull("CancellationException should not be recorded.", executor.getException());
        Assert.assertNull("CancellationException should not be recorded.", task.getException());
    }

    @Test
    public void testRejectedExecutionException() {
        Schedulers.defaultScheduler();
        Schedulers.shutdown();

        Task<?> task = Task.runAsync(() -> {
            Thread.sleep(1000);
        });

        boolean result = task.test();

        Assert.assertFalse("Task should fail since ExecutorService is shut down and RejectedExecutionException should be thrown", result);
        Assert.assertTrue("RejectedExecutionException should be recorded", task.getException() instanceof RejectedExecutionException);
    }
}