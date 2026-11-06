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
    /// A large remaining download used when only the sampling window is under test.
    private static final long CONTENT_LENGTH = 100 * 1024 * 1024;

    /// A new connection receives a complete observation window before it can be retried.
    @Test
    public void waitsForCompleteObservationWindow() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(SECOND);
        assertFalse(policy.shouldRetry(1, CONTENT_LENGTH, 10 * SECOND));
        assertTrue(policy.shouldRetry(1, CONTENT_LENGTH, 11 * SECOND));
    }

    /// An earlier fast window does not hide a later persistently slow window.
    @Test
    public void measuresSuccessiveWindowsIndependently() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        long downloaded = 128 * 1024 * 10;
        assertFalse(policy.shouldRetry(downloaded, CONTENT_LENGTH, 10 * SECOND));
        assertFalse(policy.shouldRetry(downloaded + 1, CONTENT_LENGTH, 19 * SECOND));
        assertTrue(policy.shouldRetry(downloaded + 1, CONTENT_LENGTH, 20 * SECOND));
    }

    /// Delayed samples use their actual elapsed duration instead of the nominal window length.
    @Test
    public void accountsForDelayedSamples() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        assertTrue(policy.shouldRetry(128 * 1024 * 10, CONTENT_LENGTH, 20 * SECOND));
    }

    /// Healthy windows preserve the retry budget, which remains bounded across new connections.
    @Test
    public void retainsRetryBudgetAcrossAttempts() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        long windowBytes = 128 * 1024 * 10;
        for (int window = 1; window <= 5; window++) {
            assertFalse(policy.shouldRetry(windowBytes * window, CONTENT_LENGTH, window * 10 * SECOND));
        }
        assertTrue(policy.shouldRetry(windowBytes * 5 + 1, CONTENT_LENGTH, 60 * SECOND));

        policy.startAttempt(100 * SECOND);
        assertFalse(policy.shouldRetry(1, CONTENT_LENGTH, 109 * SECOND));
        assertTrue(policy.shouldRetry(1, CONTENT_LENGTH, 110 * SECOND));

        policy.startAttempt(200 * SECOND);
        assertFalse(policy.shouldRetry(1, CONTENT_LENGTH, 210 * SECOND));
        assertFalse(policy.shouldRetry(1, CONTENT_LENGTH, 220 * SECOND));
    }

    /// Small files and nearly completed large files finish without spending a node retry.
    @Test
    public void finishesRemainingContentBeforeReselecting() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        assertFalse(policy.shouldRetry(8 * 1024, 12 * 1024, 10 * SECOND));

        policy.startAttempt(0);
        assertFalse(policy.shouldRetry(64 * 1024 * 10, 64 * 1024 * 20, 10 * SECOND));
        assertFalse(policy.shouldRetry(64 * 1024 * 20, 64 * 1024 * 20, 20 * SECOND));

        policy.startAttempt(0);
        assertTrue(policy.shouldRetry(64 * 1024 * 10, CONTENT_LENGTH, 10 * SECOND));
    }

    /// Small files still reselect a node when their remaining transfer would take too long.
    @Test
    public void reselectsStalledSmallFiles() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        assertTrue(policy.shouldRetry(1024, 12 * 1024, 10 * SECOND));
    }

    /// A missing length cannot justify abandoning a response on an estimated completion time.
    @Test
    public void keepsUnknownLengthResponses() {
        var policy = new FetchTask.BMCLAPIRetryPolicy();
        policy.startAttempt(0);
        assertFalse(policy.shouldRetry(1, -1, 10 * SECOND));
    }
}
