/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.Argument;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.game.GameInstanceLibraryBuilder;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.StringArgument;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.BOOTSTRAP_LAUNCHER;
import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.FORGE;
import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.LITELOADER;
import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.NEO_FORGE;
import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.OPTIFINE;

/// Applies launch-manifest compatibility adjustments that depend on the installed filesystem.
@NotNullByDefault
public final class LaunchManifestPreparation {
    /// Prevents construction of this utility class.
    private LaunchManifestPreparation() {
    }

    /// Prepares a normalized launch manifest using the current library files.
    ///
    /// The input must not contain inheritance or pending patches. The returned manifest may select
    /// a locally installed OptiFine artifact or replace an old BootstrapLauncher ignore list.
    ///
    /// @param repository the repository that owns the installed libraries
    /// @param manifest   the normalized launch manifest
    /// @return the manifest to use for this launch attempt
    /// @throws IllegalArgumentException if the manifest is not structurally resolved
    public static GameInstanceManifest prepare(
            GameRepository repository,
            GameInstanceManifest manifest) {
        if (manifest.inheritsFrom() != null || !manifest.getPatches().isEmpty()) {
            throw new IllegalArgumentException("Launch manifest must be structurally resolved");
        }

        GameInstanceManifest prepared = prepareBootstrapLauncher(repository, manifest);
        if (!LibraryAnalyzer.BOOTSTRAP_LAUNCHER_MAIN.equals(prepared.mainClass())) {
            prepared = prepareOptiFineLibrary(repository, prepared);
        }
        return prepared;
    }

    /// Replaces unsafe substring-based ignore-list entries used by old BootstrapLauncher versions.
    ///
    /// @param repository the repository that resolves installed classpath entries
    /// @param manifest   the normalized launch manifest
    /// @return the adjusted manifest
    private static GameInstanceManifest prepareBootstrapLauncher(
            GameRepository repository,
            GameInstanceManifest manifest) {
        if (!LibraryAnalyzer.BOOTSTRAP_LAUNCHER_MAIN.equals(manifest.mainClass())) {
            return manifest;
        }

        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(manifest, null);
        if (!analyzer.has(FORGE) && !analyzer.has(NEO_FORGE)) {
            return manifest;
        }

        if (analyzer.getVersion(BOOTSTRAP_LAUNCHER)
                .filter(version -> VersionNumber.compare(version, "0.1.17") < 0)
                .isEmpty()) {
            return manifest;
        }

        GameInstanceLibraryBuilder builder = new GameInstanceLibraryBuilder(manifest);
        List<Argument> jvmArguments = builder.getMutableJvmArguments();
        for (int i = 0; i < jvmArguments.size(); i++) {
            Argument argument = jvmArguments.get(i);
            if (argument instanceof StringArgument) {
                String value = argument.toString();
                if (value.startsWith("-DignoreList=")) {
                    jvmArguments.set(i, new StringArgument(
                            "-DignoreList=" + updateIgnoreList(
                                    repository,
                                    manifest,
                                    value.substring("-DignoreList=".length()))));
                }
            }
        }
        return builder.build();
    }

    /// Converts an old BootstrapLauncher ignore list to exact installed classpath entries.
    ///
    /// @param repository the repository that resolves installed classpath entries
    /// @param manifest   the launch manifest
    /// @param ignoreList the original comma-separated substring list
    /// @return the exact comma-separated ignore list
    private static String updateIgnoreList(
            GameRepository repository,
            GameInstanceManifest manifest,
            String ignoreList) {
        String[] ignoredSubstrings = ignoreList.split(",");
        List<String> exactEntries = new ArrayList<>();
        exactEntries.add("${primary_jar}");

        Path libraryDirectory = repository.getLayout().getLibrariesDirectory().toAbsolutePath().normalize();
        for (String classpathName : repository.getClasspath(manifest)) {
            Path classpathFile = Paths.get(classpathName).toAbsolutePath();
            String fileName = classpathFile.getFileName().toString();
            if (Stream.of(ignoredSubstrings).anyMatch(fileName::contains)) {
                String absolutePath;
                if (classpathFile.startsWith(libraryDirectory)) {
                    absolutePath = "${library_directory}${file_separator}"
                            + libraryDirectory.relativize(classpathFile).toString()
                            .replace(File.separator, "${file_separator}");
                } else {
                    absolutePath = classpathFile.toString();
                }
                exactEntries.add(StringUtils.substringBefore(absolutePath, ","));
            }
        }
        return String.join(",", exactEntries);
    }

    /// Selects the locally installed OptiFine installer artifact required by other loaders.
    ///
    /// @param repository the repository that owns the installed libraries
    /// @param manifest   the normalized launch manifest
    /// @return the adjusted manifest
    private static GameInstanceManifest prepareOptiFineLibrary(
            GameRepository repository,
            GameInstanceManifest manifest) {
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(manifest, null);
        if (!analyzer.has(OPTIFINE) || (!analyzer.has(LITELOADER) && !analyzer.has(FORGE))) {
            return manifest;
        }

        boolean removeFromClasspath = LibraryAnalyzer.MOD_LAUNCHER_MAIN.equals(manifest.mainClass());
        List<Library> libraries = new ArrayList<>();
        @Nullable Library selectedInstaller = null;

        for (Library library : manifest.getLibraries()) {
            if (library.is("optifine", "OptiFine")) {
                Library installer = new Library(
                        new Artifact("optifine", "OptiFine", library.version(), "installer"));
                if (Files.exists(repository.getLayout().getLibraryFile(manifest.id(), installer))) {
                    selectedInstaller = installer;
                } else {
                    libraries.add(library);
                }
            } else if (library.is("optifine", "launchwrapper-of")) {
                // This modified LaunchWrapper conflicts with Forge and LiteLoader.
            } else {
                libraries.add(library);
            }
        }

        if (!removeFromClasspath && selectedInstaller != null) {
            libraries.add(selectedInstaller);
        }
        return manifest.withLibraries(libraries);
    }
}
