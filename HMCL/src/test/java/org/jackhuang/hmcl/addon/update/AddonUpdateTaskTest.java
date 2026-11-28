// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.update;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.task.TaskStage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies the files left by a cancelled add-on update.
@NotNullByDefault
@EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
final class AddonUpdateTaskTest {
    /// Cancellation before replacement keeps the old file; cancellation during it publishes the new one.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void cancellationAroundReplacement(boolean duringReplacement, @TempDir Path directory) throws IOException {
        Path mods = Files.createDirectory(directory.resolve("mods"));
        Path oldFile = Files.writeString(mods.resolve("example.jar"), "old");
        Path source = Files.writeString(directory.resolve("download.jar"), "new");
        InstalledAddon local = new InstalledAddon(oldFile);
        var file = new RemoteAddon.File(Map.of(), source.toUri().toString(), oldFile.getFileName().toString());
        var version = new RemoteAddon.Version(RemoteAddon.Source.MODRINTH, "v2", "mod", "Mod", "2",
                Instant.EPOCH, RemoteAddon.VersionType.Release, file, List.of(), List.of(), List.of());
        var task = new AddonUpdateTask(new DownloadProvider(), mods, List.of(new AddonUpdate(local, version, version)));
        if (duringReplacement) {
            local.onBackup = task::cancel;
        } else {
            task.addTaskListener(new TaskListener() {
                /// Cancels after the download has been staged but before the old file is touched.
                @Override
                public void onTaskFinished(Task<?> child, TaskStage.@Nullable Scope scope, @Nullable Throwable failure) {
                    if (child instanceof FileDownloadTask && failure == null)
                        task.cancel();
                }
            });
        }

        assertThrows(CancellationException.class, task::run);
        assertEquals(duringReplacement ? "new" : "old", Files.readString(oldFile));
        if (duringReplacement)
            assertEquals("old", Files.readString(local.getFile()));
        try (var entries = Files.list(mods)) {
            assertEquals(duringReplacement ? 2 : 1, entries.count());
        }
        assertTrue(task.getFailedAddons().isEmpty());
    }

    /// An enabled add-on that retains its previous version as a backup.
    @NotNullByDefault
    private static final class InstalledAddon extends LocalAddonFile<InstalledAddon> {
        /// The installed file before replacement.
        private final Path original;
        /// The path representing the old version after replacement begins.
        private Path file;
        /// Called after moving the add-on to its backup name.
        private @Nullable Runnable onBackup;

        /// Creates a local add-on backed by the supplied file.
        private InstalledAddon(Path original) {
            this.original = original;
            this.file = original;
        }

        /// {@inheritDoc}
        @Override
        public Path getFile() {
            return file;
        }

        /// {@inheritDoc}
        @Override
        public String getFileName() {
            return "example";
        }

        /// {@inheritDoc}
        @Override
        public boolean keepOldFiles() {
            return true;
        }

        /// {@inheritDoc}
        @Override
        public void delete() throws IOException {
            Files.delete(file);
        }

        /// {@inheritDoc}
        @Override
        public void markDisabled() {
            fail("The add-on was enabled");
        }

        /// Renames the add-on and notifies the test when it becomes a backup.
        @Override
        public void setOld(boolean old) throws IOException {
            file = Files.move(file, old ? original.resolveSibling("example.jar.old") : original);
            if (old && onBackup != null)
                onBackup.run();
        }
    }
}
