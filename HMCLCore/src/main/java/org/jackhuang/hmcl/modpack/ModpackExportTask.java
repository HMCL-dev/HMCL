// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskProgress;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Exports a game instance to a temporary archive and replaces the destination after the archive closes.
@NotNullByDefault
public abstract class ModpackExportTask extends Task<@Nullable Void> {
    /// The instance snapshot supplying the game files and manifest.
    protected final DefaultGameInstance instance;

    /// The validated export configuration.
    protected final ModpackExportInfo info;

    /// The destination for the completed archive.
    private final Path destination;

    /// Creates an export of the supplied instance using the given configuration and destination.
    protected ModpackExportTask(DefaultGameInstance instance, ModpackExportInfo info, Path destination) {
        this.instance = instance;
        this.info = info.validate();
        this.destination = destination;
    }

    /// Writes the format-specific entries. The predicate selects files and directories by their
    /// slash-separated paths relative to the instance's run directory.
    protected abstract void writeArchive(Zipper zip, Predicate<String> include, TaskProgress progress) throws IOException;

    /// Writes and closes a temporary archive before replacing the destination. A failure while
    /// writing or cancellation observed before replacement leaves an existing destination intact.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws Exception {
        Path runDirectory = instance.getRunDirectory().toRealPath();
        @Nullable Path parent = destination.toAbsolutePath().getParent();
        if (parent == null || Files.isDirectory(destination))
            throw new IOException("Expected an archive file: " + destination);
        Path output = parent.toRealPath().resolve(destination.getFileName());

        List<String> exclusions = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        exclusions.add(instance.getId() + ".jar");
        exclusions.add(instance.getId() + ".json");
        // An export inside the run directory must skip prior output and concurrent staging files.
        exclusions.add("regex:(?:.*/)?\\.hmcl-export-[^/]+\\.tmp");

        LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        Path temporary = Files.createTempFile(output.getParent(), ".hmcl-export-", ".tmp");
        try {
            try (Zipper zip = new Zipper(temporary)) {
                writeArchive(zip, path -> !output.equals(runDirectory.resolve(path))
                        && Modpack.acceptFile(path, exclusions, info.getWhitelist()), context.progress());
            }
            context.checkCancellation();
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        return null;
    }
}
