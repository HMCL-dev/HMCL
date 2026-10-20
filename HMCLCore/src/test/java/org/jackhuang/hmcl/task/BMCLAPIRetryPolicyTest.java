/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests node retry decisions using elapsed time values without waiting for the wall clock.
@NotNullByDefault
public final class BMCLAPIRetryPolicyTest {
    /// One second expressed in the policy's monotonic time unit.
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    /// A new connection receives a complete observation window before it can be retried.
    @Test
    public void waitsForCompleteObservationWindow() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(SECOND);
        assertFalse(policy.shouldRetry(1, 10 * SECOND));
        assertTrue(policy.shouldRetry(1, 11 * SECOND));
    }

    /// An earlier fast window does not hide a later persistently slow window.
    @Test
    public void measuresSuccessiveWindowsIndependently() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        long downloaded = 128 * 1024 * 10;
        assertFalse(policy.shouldRetry(downloaded, 10 * SECOND));
        assertFalse(policy.shouldRetry(downloaded + 1, 19 * SECOND));
        assertTrue(policy.shouldRetry(downloaded + 1, 20 * SECOND));
    }

    /// Delayed samples use their actual elapsed duration instead of the nominal window length.
    @Test
    public void accountsForDelayedSamples() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        assertTrue(policy.shouldRetry(128 * 1024 * 10, 20 * SECOND));
    }

    /// Healthy windows preserve the retry budget, which remains bounded across new connections.
    @Test
    public void retainsRetryBudgetAcrossAttempts() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        long windowBytes = 128 * 1024 * 10;
        for (int window = 1; window <= 5; window++) {
            assertFalse(policy.shouldRetry(windowBytes * window, window * 10 * SECOND));
        }
        assertTrue(policy.shouldRetry(windowBytes * 5 + 1, 60 * SECOND));

        policy.startAttempt(100 * SECOND);
        assertFalse(policy.shouldRetry(1, 109 * SECOND));
        assertTrue(policy.shouldRetry(1, 110 * SECOND));

        policy.startAttempt(200 * SECOND);
        assertFalse(policy.shouldRetry(1, 210 * SECOND));
        assertFalse(policy.shouldRetry(1, 220 * SECOND));
    }
}
