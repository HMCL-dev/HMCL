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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// Exports the selected files from an instance and removes incomplete archives.
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

    /// Writes the archive directly to the destination and removes it if the export fails.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws IOException {
        Path runDirectory = instance.getRunDirectory().toAbsolutePath().normalize();
        Path output = destination.toAbsolutePath().normalize();
        List<String> exclusions = new ArrayList<>(ModAdviser.MODPACK_BLACK_LIST);
        exclusions.add(instance.getId() + ".jar");
        exclusions.add(instance.getId() + ".json");

        context.checkCancellation();
        try (Zipper zip = new Zipper(output)) {
            writeArchive(zip, path -> {
                context.checkCancellation();
                return !output.equals(runDirectory.resolve(path)) && Modpack.acceptFile(path, exclusions, info.getWhitelist());
            }, context.progress());
            context.commit();
        } catch (IOException | RuntimeException | Error failure) {
            if (Files.isRegularFile(output)) {
                try {
                    Files.deleteIfExists(output);
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
            }
            throw failure;
        }
        return null;
    }
}
