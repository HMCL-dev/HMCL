/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import kala.compress.archivers.ArchiveEntry;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.launch.ProcessCreationException;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Provides RenderDoc downloading, extraction, and command wrapping for game launches.
@NotNullByDefault
public final class RenderDoc {
    /// RenderDoc version downloaded from the official RenderDoc stable release.
    public static final String VERSION = "1.44";

    /// The official RenderDoc stable download URL prefix.
    private static final String DOWNLOAD_URL_PREFIX = "https://renderdoc.org/stable/";

    /// Prevents instantiation.
    private RenderDoc() {
    }

    /// Returns whether the current platform can use the bundled RenderDoc package.
    public static boolean isSupported() {
        return Architecture.SYSTEM_ARCH == Architecture.X86_64
                && (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS || OperatingSystem.CURRENT_OS == OperatingSystem.LINUX);
    }

    /// Creates a task that downloads RenderDoc if necessary and returns the renderdoccmd executable.
    public static Task<Path> prepare() {
        return prepare(getCommandExecutable());
    }

    /// Creates a task that downloads RenderDoc if necessary and returns the qrenderdoc executable.
    public static Task<Path> prepareUI() {
        return prepare(getUIExecutable());
    }

    /// Starts qrenderdoc with the current process environment.
    public static Process startUI(Path renderDocUIExecutable) throws ProcessCreationException {
        try {
            return new ProcessBuilder(FileUtils.getAbsolutePath(renderDocUIExecutable))
                    .directory(getRenderDocDirectory().toFile())
                    .start();
        } catch (IOException e) {
            throw new ProcessCreationException(e);
        }
    }

    /// Creates a task that downloads RenderDoc if necessary and returns the requested executable.
    private static Task<Path> prepare(Path executable) {
        if (!isSupported()) {
            return Task.supplyAsync(() -> {
                throw new UnsupportedOperationException("RenderDoc is only supported on Windows x86-64 and Linux x86-64");
            });
        }

        if (Files.isRegularFile(executable)) {
            return Task.completed(executable);
        }

        Path archive = getArchivePath();
        Task<?> downloadTask = Files.isRegularFile(archive)
                ? Task.completed(null)
                : new FileDownloadTask(URI.create(getDownloadUrl()), archive);

        return downloadTask.thenApplyAsync(Schedulers.io(), ignored -> {
            extract(archive, getExtractDirectory());
            return executable;
        }).withStage("renderdoc.download");
    }

    /// Builds the command prefix used to make RenderDoc capture the Java process.
    public static @Unmodifiable List<String> createWrapper(Path renderDocExecutable, Path workingDirectory) {
        return List.of(
                FileUtils.getAbsolutePath(renderDocExecutable),
                "capture",
                "--wait-for-exit",
                "--working-dir",
                FileUtils.getAbsolutePath(workingDirectory)
        );
    }

    /// Adds a RenderDoc command prefix in front of an existing wrapper command.
    public static String prependWrapper(String wrapper, Path renderDocExecutable, Path workingDirectory) {
        ArrayList<String> command = new ArrayList<>(createWrapper(renderDocExecutable, workingDirectory));
        if (wrapper != null && !wrapper.isBlank()) {
            command.addAll(org.jackhuang.hmcl.util.StringUtils.tokenize(wrapper));
        }
        return new CommandBuilder().addAll(command).toString();
    }

    /// Extracts the RenderDoc archive into the destination directory.
    private static void extract(Path archive, Path destination) throws IOException {
        Files.createDirectories(destination);

        try (ArchiveFileTree<?, ?> tree = ArchiveFileTree.open(archive)) {
            extractDirectory(tree, tree.getRoot(), destination);
        }

        FileUtils.setExecutable(getCommandExecutable());
    }

    /// Extracts an archive directory and all of its children while preserving directory structure.
    private static void extractDirectory(ArchiveFileTree<?, ?> tree, ArchiveFileTree.Dir<?> directory, Path destination) throws IOException {
        Files.createDirectories(destination);

        for (ArchiveFileTree.Dir<?> childDirectory : directory.getSubDirs().values()) {
            extractDirectory(tree, childDirectory, destination.resolve(childDirectory.getName()));
        }

        for (ArchiveEntry entry : directory.getFiles().values()) {
            if (entry.isDirectory()) {
                continue;
            }

            Path output = destination.resolve(Path.of(entry.getName()).getFileName().toString());
            Files.createDirectories(output.getParent());
            extractEntry(tree, entry, output);
        }
    }

    /// Extracts one archive entry and restores executable permission when the archive records it.
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void extractEntry(ArchiveFileTree tree, ArchiveEntry entry, Path output) throws IOException {
        if (tree.isLink(entry)) {
            Files.deleteIfExists(output);
            Files.createSymbolicLink(output, Path.of(tree.getLink(entry)));
            return;
        }

        Files.deleteIfExists(output);
        tree.extractTo(entry, output);
        if (tree.isExecutable(entry)) {
            FileUtils.setExecutable(output);
        }
    }

    /// Returns the expected path of renderdoccmd after extraction.
    private static Path getCommandExecutable() {
        return getRenderDocDirectory().resolve(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "renderdoccmd.exe" : "bin/renderdoccmd");
    }

    /// Returns the expected path of qrenderdoc after extraction.
    private static Path getUIExecutable() {
        return getRenderDocDirectory().resolve(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "qrenderdoc.exe" : "bin/qrenderdoc");
    }

    /// Returns the local archive cache path.
    private static Path getArchivePath() {
        return getBaseDirectory().resolve(getArchiveName());
    }

    /// Returns the directory where the archive is extracted.
    private static Path getExtractDirectory() {
        return getBaseDirectory().resolve("extracted");
    }

    /// Returns the extracted RenderDoc root directory.
    private static Path getRenderDocDirectory() {
        return getExtractDirectory().resolve(getBaseName());
    }

    /// Returns HMCL's dependency directory for this RenderDoc version and operating system.
    private static Path getBaseDirectory() {
        return Metadata.DEPENDENCIES_DIRECTORY.resolve("renderdoc").resolve(VERSION).resolve(OperatingSystem.CURRENT_OS.getCheckedName());
    }

    /// Returns the remote RenderDoc archive URL.
    private static String getDownloadUrl() {
        return URI.create(DOWNLOAD_URL_PREFIX + VERSION + "/" + getArchiveName()).toString();
    }

    /// Returns the platform-specific RenderDoc archive file name.
    private static String getArchiveName() {
        return getBaseName() + (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? ".zip" : ".tar.gz");
    }

    /// Returns the platform-specific RenderDoc archive and root directory base name.
    private static String getBaseName() {
        return (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS ? "RenderDoc_%s_64" : "renderdoc_%s")
                .formatted(VERSION);
    }
}
