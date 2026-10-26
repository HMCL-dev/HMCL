// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.application.Platform;
import javafx.animation.AnimationTimer;
import org.jackhuang.hmcl.JavaFXLauncher;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/// Runs feedback assertions on a real JavaFX toolkit, failing rather than skipping if unavailable.
@NotNullByDefault
final class FeedbackTestSupport {
    /// Prevents construction of the test utility.
    private FeedbackTestSupport() {
    }

    /// Runs a value-producing operation on the JavaFX application thread.
    static <T> T fx(Callable<T> action) {
        assertTrue(JavaFXLauncher.isStarted(), "A working JavaFX toolkit is required");
        FutureTask<T> task = new FutureTask<>(() -> {
            Platform.setImplicitExit(false);
            return action.call();
        });
        Platform.runLater(task);
        try {
            return task.get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /// Runs an assertion block on the JavaFX application thread.
    static void runFx(Runnable action) {
        fx(() -> {
            action.run();
            return true;
        });
    }

    /// Waits for native input or window state to reach an expected condition without blocking FX.
    static void awaitFx(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!fx(condition::getAsBoolean)) {
            if (System.nanoTime() >= deadline)
                fail("JavaFX condition did not become true");
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }

    /// Waits for native window geometry and rendering to settle before sending pointer input.
    static void awaitPulses() {
        CompletableFuture<Boolean> completion = new CompletableFuture<>();
        runFx(() -> new AnimationTimer() {
            /// The number of completed pulses since this wait began.
            private int pulses;

            /// Completes the wait after three pulses without blocking the application thread.
            @Override
            public void handle(long now) {
                if (++pulses == 3) {
                    stop();
                    completion.complete(true);
                }
            }
        }.start());
        try {
            completion.get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
