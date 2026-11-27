// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.modpack.ModpackConfiguration.FileInformation;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;

/// Extracts local overrides and preserves user files during modpack updates.
@NotNullByDefault
public final class ModpackInstallTask extends Task<@Nullable Void> {
    /// Archive containing the overrides.
    private final Path modpackFile;
    /// Root of the installed overrides.
    private final Path dest;
    /// Archive directories in extraction order.
    private final @Unmodifiable List<String> subDirectories;
    /// Selects files to extract.
    private final Predicate<String> filter;
    /// Installed configuration to update, or null for a new installation.
    private final @Nullable Path configurationFile;

    /// Creates an override extraction task for a new installation or an existing configuration.
    public ModpackInstallTask(Path modpackFile, Path dest, List<String> subDirectories, Predicate<String> filter,
                              @Nullable Path configurationFile) {
        this.modpackFile = modpackFile;
        this.dest = dest.toAbsolutePath().normalize();
        this.subDirectories = List.copyOf(subDirectories);
        this.filter = filter;
        this.configurationFile = configurationFile == null ? null : configurationFile.toAbsolutePath().normalize();
    }

    /// Extracts overrides and records completed changes even when a later entry fails.
    @Override
    public @Nullable Void execute(TaskExecutionContext context) throws IOException {
        @Nullable ModpackConfiguration<?> installed = readConfiguration();
        Map<String, String> hashes = new LinkedHashMap<>();
        if (installed != null) {
            for (FileInformation file : installed.getOverrides())
                hashes.put(file.getPath(), file.getHash());
        }
        Set<String> retired = new LinkedHashSet<>(hashes.keySet());
        try (Closeable saveProgress = () -> saveOverrides(installed, hashes)) {
            for (String subDirectory : subDirectories) {
                new Unzipper(modpackFile, dest)
                        .setSubDirectory(subDirectory)
                        .setTerminateIfSubDirectoryNotExists()
                        .setReplaceExistentFile(true)
                        .setFilter((entry, file, path) -> {
                            context.checkCancellation();
                            if (entry.isDirectory()) return true;
                            if (!filter.test(path)) return false;
                            retired.remove(path);
                            return configurationFile == null || canReplace(file, hashes.get(path), context);
                        })
                        .unzip(configurationFile == null ? null : "SHA-1", hashes);
            }
            for (String path : retired) {
                Modpack.removeUnmodifiedOverride(dest, path, hashes.get(path), context);
                hashes.remove(path);
            }
        }
        return null;
    }

    /// Reads the current override ownership after the installer has reserved the instance.
    private @Nullable ModpackConfiguration<?> readConfiguration() {
        if (configurationFile == null) return null;
        try {
            return JsonUtils.fromJsonFile(configurationFile, ModpackConfiguration.typeOf(JsonElement.class));
        } catch (IOException | JsonParseException ignored) {
            // With no usable baseline, existing files are treated as user files.
            return null;
        }
    }

    /// Replaces managed files only while unmodified, and saves a copy of an unmanaged conflict.
    private boolean canReplace(Path file, @Nullable String installedHash, TaskExecutionContext context) throws IOException {
        if (file.equals(configurationFile) || file.equals(dest))
            throw new IOException("Invalid override path: " + file);
        for (Path parent = file.getParent(); !parent.equals(dest); parent = parent.getParent()) {
            if (Files.isSymbolicLink(parent))
                throw new IOException("Override directory is a symbolic link: " + parent);
        }
        if (installedHash != null) {
            if (Files.isSymbolicLink(file)) {
                String target = Files.readSymbolicLink(file).toString();
                return installedHash.equalsIgnoreCase(DigestUtils.digestToString("SHA-1", target.getBytes(StandardCharsets.UTF_8)));
            }
            return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                    && installedHash.equalsIgnoreCase(DigestUtils.digestToString("SHA-1", file, context::checkCancellation));
        }

        if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file))
                throw new IOException("Expected a file at override path: " + file);
            Path backup = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".bak");
            Files.copy(file, backup, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.COPY_ATTRIBUTES);
        }
        return true;
    }

    /// Saves the installed file hashes without advancing the pack manifest or version.
    private void saveOverrides(@Nullable ModpackConfiguration<?> installed, Map<String, String> hashes) throws IOException {
        if (installed == null || configurationFile == null) return;
        Map<String, FileInformation> previousFiles = new HashMap<>();
        for (FileInformation file : installed.getOverrides())
            previousFiles.put(file.getPath(), file);
        List<FileInformation> overrides = new ArrayList<>(hashes.size());
        hashes.forEach((path, hash) -> {
            @Nullable FileInformation previous = previousFiles.get(path);
            overrides.add(previous != null && hash.equalsIgnoreCase(previous.getHash()) ? previous : new FileInformation(path, hash));
        });
        if (installed.getOverrides().equals(overrides)) return;

        boolean interrupted = Thread.interrupted();
        try {
            installed.setOverrides(overrides).writeTo(configurationFile);
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
