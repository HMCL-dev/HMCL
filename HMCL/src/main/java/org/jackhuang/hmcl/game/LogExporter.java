/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class LogExporter {
    private LogExporter() {
    }

    public static CompletableFuture<Void> exportLogs(Path zipFile, DefaultGameRepository gameRepository, String versionId, String logs, String launchScript) {
        Path runDirectory = gameRepository.getRunDirectory(versionId).toPath();
        Path baseDirectory = gameRepository.getBaseDirectory().toPath();
        List<String> versions = new ArrayList<>();

        String currentVersionId = versionId;
        HashSet<String> resolvedSoFar = new HashSet<>();
        while (true) {
            if (resolvedSoFar.contains(currentVersionId)) break;
            resolvedSoFar.add(currentVersionId);
            Version currentVersion = gameRepository.getVersion(currentVersionId);
            versions.add(currentVersionId);

            if (StringUtils.isNotBlank(currentVersion.getInheritsFrom())) {
                currentVersionId = currentVersion.getInheritsFrom();
            } else {
                break;
            }
        }

        return CompletableFuture.runAsync(() -> {
            try (Zipper zipper = new Zipper(zipFile)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(runDirectory.resolve("liteconfig"), "*.log")) {
                    long processStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            FileTime time = Files.readAttributes(file, BasicFileAttributes.class).creationTime();
                            if (time.toMillis() >= processStartTime) {
                                String crashLog = Logging.filterForbiddenToken(FileUtils.readText(file));
                                zipper.putTextFile(crashLog, file.getFileName().toString());
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Failed to find any log on liteconfig", e);
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(runDirectory.resolve("logs"), "*.log")) {
                    long processStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            FileTime time = Files.readAttributes(file, BasicFileAttributes.class).creationTime();
                            if (time.toMillis() >= processStartTime) {
                                String crashLog = Logging.filterForbiddenToken(FileUtils.readText(file));
                                zipper.putTextFile(crashLog, file.getFileName().toString());
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Failed to find any log on logs", e);
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(runDirectory, "*.log")) {
                    long processStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            FileTime time = Files.readAttributes(file, BasicFileAttributes.class).creationTime();
                            if (time.toMillis() >= processStartTime) {
                                String crashLog = Logging.filterForbiddenToken(FileUtils.readText(file));
                                zipper.putTextFile(crashLog, file.getFileName().toString());
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Failed to find any log on runDirectory", e);
                }

                try (DirectoryStream<Path> stream = Files.newDirectoryStream(runDirectory.resolve("crash-reports"), "*.txt")) {
                    long processStartTime = ManagementFactory.getRuntimeMXBean().getStartTime();

                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            FileTime time = Files.readAttributes(file, BasicFileAttributes.class).creationTime();
                            if (time.toMillis() >= processStartTime) {
                                String crashLog = Logging.filterForbiddenToken(FileUtils.readText(file));
                                zipper.putTextFile(crashLog, file.getFileName().toString());
                            }
                        }
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "Failed to find any txt log on crash-reports", e);
                }

                zipper.putTextFile(Logging.getLogs(), "hmcl.log");
                zipper.putTextFile(logs, "minecraft.log");
                zipper.putTextFile(Logging.filterForbiddenToken(launchScript), OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "launch.bat" : "launch.sh");

                for (String id : versions) {
                    Path versionJson = baseDirectory.resolve("versions").resolve(id).resolve(id + ".json");
                    if (Files.exists(versionJson)) {
                        zipper.putFile(versionJson, id + ".json");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
