// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author Glavo
 */
@NotNullByDefault
public final class WorldBackupTask extends Task<Path> {

    private final World world;
    private final Path backupsDir;

    public WorldBackupTask(World world, Path backupsDir) {
        this.world = world;
        this.backupsDir = backupsDir;
    }

    @Override
    public void execute() throws Exception {
        boolean needLock = !world.holdsLock();
        try (World ignore = needLock ? world.lock() : null) {
            Files.createDirectories(backupsDir);
            String time = LocalDateTime.now().format(WorldBackupsPage.TIME_FORMATTER);
            String baseName = time + "_" + world.getFileName();
            Path backupFile = null;
            OutputStream outputStream = null;

            int count;
            for (count = 0; count < 256; count++) {
                try {
                    backupFile = backupsDir.resolve(baseName + (count == 0 ? "" : " " + count) + ".zip").toAbsolutePath();
                    outputStream = Files.newOutputStream(backupFile, StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
                    break;
                } catch (FileAlreadyExistsException ignored) {
                }
            }

            if (outputStream == null)
                throw new IOException("Too many attempts");

            try (ZipOutputStream zipOutputStream = new ZipOutputStream(new BufferedOutputStream(outputStream))) {
                String rootName = world.getFileName();
                Path rootDir = this.world.getFile();
                Files.walkFileTree(this.world.getFile(), new SimpleFileVisitor<>() {
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

            setResult(backupFile);
        }
    }
}
