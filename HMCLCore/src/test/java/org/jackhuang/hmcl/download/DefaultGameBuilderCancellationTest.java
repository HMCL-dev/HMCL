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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies that cancelled builds retain exclusivity only until their cleanup completes.
@NotNullByDefault
public final class DefaultGameBuilderCancellationTest {

    /// Explicit cleanup after an early cancellation releases a draft skipped by the task chain.
    @Test
    public void testAbortAfterCancelBeforeBuildStarts(@TempDir Path directory) throws Exception {
        verifyCancellation(directory, false);
    }

    /// Cancellation waits for active component work before releasing the draft.
    @Test
    public void testAbortAfterCancelDuringComponentInstallation(@TempDir Path directory) throws Exception {
        verifyCancellation(directory, true);
    }

    /// Runs an installation with a gate at the chosen cancellation point.
    private static void verifyCancellation(Path directory, boolean duringInstallation) throws Exception {
        TestRepository repository = new TestRepository(directory);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        AtomicBoolean componentStarted = new AtomicBoolean();
        TestDependencyManager manager = new TestDependencyManager(
                repository, ready, release, componentStarted);
        AtomicReference<@Nullable IOException> cleanupFailure = new AtomicReference<>();
        DefaultGameBuilder builder = manager.newGameBuilder(new GameInstanceID("first"));
        Task<?> task;
        try (builder) {
            builder.component(GameComponentType.GAME, "1.21.1");
            task = builder.buildAsync();
        }
        TaskExecutor executor = task.executor(new TaskListener() {
            /// Holds the outer stage wrapper before its cleanup task can be scheduled.
            @Override
            public void onReady(Task<?> readyTask) {
                if (!duringInstallation && readyTask == task) {
                    ready.countDown();
                    await(release);
                }
            }

            /// Releases the draft only after the executor stops, as the installation dialog does.
            @Override
            public void onStop(boolean success, TaskExecutor completedExecutor) {
                try {
                    builder.abort();
                    builder.abort();
                } catch (IOException exception) {
                    cleanupFailure.set(exception);
                } finally {
                    stopped.countDown();
                }
            }
        });
        executor.start();
        try {
            await(ready);
            executor.cancel();
            assertThrows(IllegalStateException.class, repository::openDraft);
            assertEquals(1L, stopped.getCount());
        } finally {
            release.countDown();
        }
        await(stopped);

        assertNull(cleanupFailure.get());
        assertEquals(duringInstallation, componentStarted.get());
        assertFalse(repository.hasInstance(new GameInstanceID("first")));
        try (DefaultGameBuilder ignored = manager.newGameBuilder(new GameInstanceID("second"))) {
            assertThrows(IllegalStateException.class, repository::openDraft);
        }
    }

    /// Waits for a deterministic test gate and fails instead of hanging indefinitely.
    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS), "Task lifecycle timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    /// Provides an empty repository for builds that never publish an instance.
    @NotNullByDefault
    private static final class TestRepository extends DefaultGameRepository {
        /// Creates a repository rooted at the test directory.
        private TestRepository(Path directory) {
            super(directory);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameRepositoryLayout createLayout(Path directory) {
            return new DefaultGameRepositoryLayout(directory);
        }

        /// Rejects publication because every test cancels before an instance is installed.
        @Override
        protected DefaultGameInstance createInstance(DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id, GameInstanceManifest manifest, @Nullable Path manifestFile) {
            throw new AssertionError("Cancelled installation must not publish an instance");
        }
    }

    /// Replaces network installation with a controlled local task.
    @NotNullByDefault
    private static final class TestDependencyManager extends DefaultDependencyManager {
        /// Signals when component installation begins.
        private final CountDownLatch ready;
        /// Allows component installation to return after cancellation.
        private final CountDownLatch release;
        /// Records whether component installation ran.
        private final AtomicBoolean componentStarted;

        /// Creates a manager with an isolated cache and no remote requests.
        private TestDependencyManager(TestRepository repository, CountDownLatch ready,
                CountDownLatch release, AtomicBoolean componentStarted) {
            super(repository, new MojangDownloadProvider(),
                    new DefaultCacheRepository(repository.getBaseDirectory().resolve("cache")));
            this.ready = ready;
            this.release = release;
            this.componentStarted = componentStarted;
        }

        /// Returns a task that waits for cancellation before reporting a local installation failure.
        @Override
        Task<GameInstanceManifest> installUnpublishedComponentAsync(GameInstanceManifest manifest,
                Path modsDirectory, String gameVersion, GameComponentType componentType, String version) {
            return Task.supplyAsync(() -> {
                componentStarted.set(true);
                ready.countDown();
                await(release);
                throw new IOException("Simulated component installation failure");
            });
        }
    }
}
