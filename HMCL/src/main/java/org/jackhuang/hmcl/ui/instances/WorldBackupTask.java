// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Glavo
 */
public final class WorldBackupTask extends Task<Path> {

    private final World world;
    private final Path backupsDir;
    private final boolean needLock;

    public WorldBackupTask(World world, Path backupsDir, boolean needLock) {
        this.world = world;
        this.backupsDir = backupsDir;
        this.needLock = needLock;
    }

    /// Creates a ZIP backup and returns its absolute path.
    @Override
    public Path execute(TaskExecutionContext context) throws Exception {
        try (@Nullable FileChannel lockChannel = needLock ? world.lock() : null) {
            Files.createDirectories(backupsDir);
            String time = LocalDateTime.now().format(WorldBackupsPage.TIME_FORMATTER);
            String baseName = time + "_" + world.getFileName();
            @Nullable Path backupFile = null;
            @Nullable OutputStream outputStream = null;

            int count;
            for (count = 0; count < 256; count++) {
                try {
                    backupFile = backupsDir.resolve(baseName + (count == 0 ? "" : " " + count) + ".zip").toAbsolutePath();
                    outputStream = Files.newOutputStream(backupFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
                    break;
                } catch (FileAlreadyExistsException ignored) {
                }
            }

            if (outputStream == null || backupFile == null)
                throw new IOException("Too many attempts");

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
                String rootName = world.getFileName();
                Path rootDir = this.world.getFile();
                Files.walkFileTree(this.world.getFile(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        if (path.endsWith("session.lock")) {
                            return FileVisitResult.CONTINUE;
                        }
                        zipOutputStream.putNextEntry(new ZipEntry(rootName + "/" + rootDir.relativize(path).toString().replace('\\', '/')));
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            return backupFile;
        }
    }
}
