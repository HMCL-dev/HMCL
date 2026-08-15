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

import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class LogExporter {
    private LogExporter() {
    }

    public static CompletableFuture<Void> exportLogs(
            Path zipFile, DefaultGameRepository repository, GameInstanceID instanceId, String logs, String launchScript,
            PathMatcher logMatcher) {
        Path runDirectory = repository.getRunDirectory(instanceId);
        Path baseDirectory = repository.getBaseDirectory();
        List<GameInstanceID> instances = new ArrayList<>();

        GameInstanceID currentInstanceId = instanceId;
        HashSet<GameInstanceID> resolvedSoFar = new HashSet<>();
        while (true) {
            if (resolvedSoFar.contains(currentInstanceId)) break;
            resolvedSoFar.add(currentInstanceId);
            GameInstanceManifest currentVersion = repository.getInstanceManifest(currentInstanceId);
            instances.add(currentInstanceId);

            if (currentVersion.inheritsFrom() != null) {
                currentInstanceId = currentVersion.inheritsFrom();
            } else {
                break;
            }
        }

        return CompletableFuture.runAsync(() -> {
            try (Zipper zipper = new Zipper(zipFile, true)) {
                processLogs(runDirectory.resolve("liteconfig"), "*.log", "liteconfig", zipper, logMatcher);
                processLogs(runDirectory.resolve("logs"), "*.log", "logs", zipper, logMatcher);
                processLogs(runDirectory, "*.log", "runDirectory", zipper, logMatcher);
                processLogs(runDirectory.resolve("crash-reports"), "*.txt", "crash-reports", zipper, logMatcher);

                zipper.putTextFile(LOG.getLogs(), "hmcl.log");
                zipper.putTextFile(logs, "minecraft.log");
                zipper.putTextFile(Logger.filterForbiddenToken(launchScript), OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "launch.bat" : "launch.sh");

                for (GameInstanceID id : instances) {
                    Path instanceJson = repository.getInstanceJson(id);
                    if (Files.exists(instanceJson)) {
                        zipper.putFile(instanceJson, id + ".json");
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private static void processLogs(Path directory, String fileExtension, String logDirectory, Zipper zipper, PathMatcher logMatcher) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, fileExtension)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    if (logMatcher == null || logMatcher.matches(file)) {
                        try (BufferedReader reader = IOUtils.newBufferedReaderMaybeNativeEncoding(file)) {
                            zipper.putLines(reader.lines().map(Logger::filterForbiddenToken), file.getFileName().toString());
                        } catch (IOException e) {
                            LOG.warning("Failed to read log file: " + file, e);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to find any log on " + logDirectory, e);
        }
    }
}
