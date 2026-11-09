// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.Zipper;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
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
            Path zipFile, DefaultGameInstance instance, LaunchOptions options, String logs, String launchScript,
            PathMatcher logMatcher) {
        DefaultGameRepositorySnapshot repositorySnapshot = instance.getSnapshot();
        Path runDirectory = options.getGameDir();
        List<GameInstanceID> instances = new ArrayList<>();

        GameInstanceID currentInstanceId = instance.id;
        HashSet<GameInstanceID> resolvedSoFar = new HashSet<>();
        while (true) {
            if (resolvedSoFar.contains(currentInstanceId)) break;
            resolvedSoFar.add(currentInstanceId);
            @Nullable DefaultGameInstance currentInstance = repositorySnapshot.get(currentInstanceId);
            if (currentInstance == null)
                break;
            instances.add(currentInstanceId);

            if (currentInstance.getManifest().inheritsFrom() != null) {
                currentInstanceId = currentInstance.getManifest().inheritsFrom();
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
                    @Nullable DefaultGameInstance currentInstance = repositorySnapshot.get(id);
                    if (currentInstance != null) {
                        try (var writer = new OutputStreamWriter(zipper.putStream(id + ".json"), StandardCharsets.UTF_8)) {
                            JsonUtils.GSON.toJson(currentInstance.getManifest(), writer);
                        }
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
