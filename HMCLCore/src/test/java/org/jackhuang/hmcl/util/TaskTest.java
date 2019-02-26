package org.jackhuang.hmcl.util;

import javafx.embed.swing.JFXPanel;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.junit.Assert;
import org.junit.Test;

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
        }).withRun(() -> {
            bool.set(true);
        }).test();

        Assert.assertTrue("Task should success because withRun will ignore previous exception", success);
        Assert.assertTrue("withRun should be executed", bool.get());
    }

    @Test
    public void testThenAccept() {
        new JFXPanel(); // init JavaFX Toolkit
        AtomicBoolean flag = new AtomicBoolean();
        boolean result = Task.supplyAsync(JavaVersion::fromCurrentEnvironment)
                .thenAccept(Schedulers.javafx(), javaVersion -> {
                    flag.set(true);
                    Assert.assertEquals(javaVersion, JavaVersion.fromCurrentEnvironment());
                })
                .test();

        Assert.assertTrue("Task does not succeed", result);
        Assert.assertTrue("ThenAccept has not been executed", flag.get());
    }
}