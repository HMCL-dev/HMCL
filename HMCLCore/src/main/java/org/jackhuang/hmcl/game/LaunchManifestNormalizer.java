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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

/// Normalizes a structurally resolved manifest into the stable view consumed by launch-time code.
///
/// Normalization depends only on manifest content. Filesystem-dependent compatibility adjustments
/// are performed separately immediately before launch.
@NotNullByDefault
public final class LaunchManifestNormalizer {
    /// Prevents construction of this utility class.
    private LaunchManifestNormalizer() {
    }

    /// Normalizes a resolved launch manifest.
    ///
    /// The input must not contain inheritance or pending patches. The returned manifest has duplicate
    /// libraries removed and loader-specific arguments and libraries repaired. The input is unchanged.
    ///
    /// @param manifest the structurally resolved launch manifest
    /// @return the normalized launch manifest
    /// @throws IllegalArgumentException if the manifest still contains inheritance or pending patches
    public static GameInstanceManifest normalize(GameInstanceManifest manifest) {
        if (manifest.inheritsFrom() != null || !manifest.getPatches().isEmpty()) {
            throw new IllegalArgumentException("Launch manifest must be structurally resolved");
        }

        GameInstanceManifest normalized = uniqueLibraries(manifest);
        @Nullable String mainClass = normalized.mainClass();

        if (GameComponentAnalyzer.LAUNCH_WRAPPER_MAIN.equals(mainClass)) {
            normalized = normalizeLaunchWrapper(normalized, true);
            if (GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(normalized.mainClass())) {
                normalized = normalizeModLauncher(normalized);
            }
        } else if (GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(mainClass)) {
            normalized = normalizeModLauncher(normalized);
        } else if (GameComponentAnalyzer.BOOTSTRAP_LAUNCHER_MAIN.equals(mainClass)) {
            normalized = normalizeBootstrapLauncher(normalized);
        }

        return removeLegacyLog4jPatch(normalized);
    }

    /// Repairs LaunchWrapper tweak-class configuration.
    ///
    /// @param manifest          the resolved manifest
    /// @param reorderTweakClass whether retained tweak classes are moved to their required positions
    /// @return the repaired manifest
    private static GameInstanceManifest normalizeLaunchWrapper(
            GameInstanceManifest manifest,
            boolean reorderTweakClass) {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, null);
        GameInstanceLibraryBuilder builder = new GameInstanceLibraryBuilder(manifest);
        @Nullable String mainClass = null;

        // Forge installers may replace the complete argument list, so compatible tweakers must be
        // restored in deterministic order.
        if (analyzer.has(GameComponentType.LITELOADER) && !analyzer.hasModLauncher()) {
            builder.replaceTweakClass(
                    GameComponentAnalyzer.LITELOADER_TWEAKER,
                    GameComponentAnalyzer.LITELOADER_TWEAKER,
                    !reorderTweakClass,
                    reorderTweakClass);
        } else {
            builder.removeTweakClass(GameComponentAnalyzer.LITELOADER_TWEAKER);
        }

        if (analyzer.has(GameComponentType.OPTIFINE)) {
            if (!analyzer.has(GameComponentType.LITELOADER) && !analyzer.has(GameComponentType.FORGE)) {
                if (builder.hasTweakClass(GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(1))) {
                    builder.replaceTweakClass(
                            GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(1),
                            GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(0),
                            !reorderTweakClass,
                            reorderTweakClass);
                }
            } else if (analyzer.hasModLauncher()) {
                mainClass = GameComponentAnalyzer.MOD_LAUNCHER_MAIN;
                for (String optiFineTweaker : GameComponentAnalyzer.OPTIFINE_TWEAKERS) {
                    builder.removeTweakClass(optiFineTweaker);
                }
            } else if (builder.hasTweakClass(GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(0))) {
                builder.replaceTweakClass(
                        GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(0),
                        GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(1),
                        !reorderTweakClass,
                        reorderTweakClass);
            }
        } else {
            for (String optiFineTweaker : GameComponentAnalyzer.OPTIFINE_TWEAKERS) {
                builder.removeTweakClass(optiFineTweaker);
            }
        }

        boolean hasForge = analyzer.has(GameComponentType.FORGE);
        boolean hasModLauncher = analyzer.hasModLauncher();
        for (String forgeTweaker : GameComponentAnalyzer.FORGE_TWEAKERS) {
            if (!hasForge) {
                builder.removeTweakClass(forgeTweaker);
            } else if (!hasModLauncher && builder.hasTweakClass(forgeTweaker)) {
                builder.replaceTweakClass(
                        forgeTweaker,
                        forgeTweaker,
                        !reorderTweakClass,
                        reorderTweakClass);
            }
        }

        GameInstanceManifest normalized = builder.build();
        return mainClass == null ? normalized : normalized.withMainClass(mainClass);
    }

    /// Adds the transformer discovery service required by Forge and OptiFine on ModLauncher.
    ///
    /// @param manifest the resolved manifest
    /// @return the repaired manifest
    private static GameInstanceManifest normalizeModLauncher(GameInstanceManifest manifest) {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, null);
        if (!analyzer.has(GameComponentType.FORGE) || !analyzer.has(GameComponentType.OPTIFINE)) {
            return manifest;
        }

        GameInstanceLibraryBuilder builder = new GameInstanceLibraryBuilder(manifest);
        Library transformerDiscoveryService = new Library(
                new Artifact("org.jackhuang.hmcl", "transformer-discovery-service", "1.0"));
        boolean servicePresent = manifest.getLibraries().stream()
                .anyMatch(library -> library.is("org.jackhuang.hmcl", "transformer-discovery-service"));

        manifest.getLibraries().stream()
                .filter(library -> library.is("optifine", "OptiFine"))
                .findAny()
                .ifPresent(optiFine -> {
                    String candidateArgument =
                            "-Dhmcl.transformer.candidates=${library_directory}/" + optiFine.getPath();
                    List<Argument> jvmArguments = builder.getMutableJvmArguments();
                    if (jvmArguments.stream().noneMatch(argument -> candidateArgument.equals(argument.toString()))) {
                        jvmArguments.add(new StringArgument(candidateArgument));
                    }
                    if (!servicePresent) {
                        builder.addLibrary(transformerDiscoveryService);
                    }
                });

        return builder.build();
    }

    /// Repairs the filesystem-independent BootstrapLauncher ignore-list form.
    ///
    /// BootstrapLauncher 0.1.17 and newer compare ignore-list entries only with file names, so the
    /// primary jar placeholder can be added without inspecting the installed classpath.
    ///
    /// @param manifest the resolved manifest
    /// @return the repaired manifest
    private static GameInstanceManifest normalizeBootstrapLauncher(GameInstanceManifest manifest) {
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, null);
        if (!analyzer.has(GameComponentType.FORGE) && !analyzer.has(GameComponentType.NEO_FORGE)) {
            return manifest;
        }

        if (Optional.ofNullable(analyzer.getVersion(GameComponentType.BOOTSTRAP_LAUNCHER))
                .filter(version -> VersionNumber.compare(version, "0.1.17") >= 0)
                .isEmpty()) {
            return manifest;
        }

        GameInstanceLibraryBuilder builder = new GameInstanceLibraryBuilder(manifest);
        List<Argument> jvmArguments = builder.getMutableJvmArguments();
        for (int i = 0; i < jvmArguments.size(); i++) {
            Argument argument = jvmArguments.get(i);
            if (argument instanceof StringArgument) {
                String value = argument.toString();
                if (value.startsWith("-DignoreList=")
                        && !containsCommaSeparatedValue(
                                value.substring("-DignoreList=".length()), "${primary_jar_name}")) {
                    jvmArguments.set(i, new StringArgument(value + ",${primary_jar_name}"));
                }
            }
        }
        return builder.build();
    }

    /// Returns whether a comma-separated list contains the exact requested value.
    ///
    /// @param values the comma-separated values
    /// @param target the value to find
    /// @return whether `target` is present
    private static boolean containsCommaSeparatedValue(String values, String target) {
        for (String value : values.split(",")) {
            if (target.equals(value)) {
                return true;
            }
        }
        return false;
    }

    /// Removes the obsolete HMCL Log4j patch formerly prepended to affected manifests.
    ///
    /// @param manifest the normalized manifest
    /// @return the manifest without the obsolete first library, when present
    private static GameInstanceManifest removeLegacyLog4jPatch(GameInstanceManifest manifest) {
        List<Library> libraries = manifest.getLibraries();
        if (libraries.isEmpty()) {
            return manifest;
        }

        Library library = libraries.get(0);
        if ("org.glavo".equals(library.groupId())
                && ("log4j-patch".equals(library.artifactId())
                || "log4j-patch-beta9".equals(library.artifactId()))
                && "1.0".equals(library.version())) {
            return manifest.withLibraries(libraries.subList(1, libraries.size()));
        }
        return manifest;
    }

    /// Removes redundant library declarations while retaining rule-distinct variants.
    ///
    /// For equal compatibility rules, the newer version wins. Identical coordinates retain the
    /// declaration with the richer serialized metadata.
    ///
    /// @param manifest the resolved manifest
    /// @return the manifest with redundant libraries removed
    private static GameInstanceManifest uniqueLibraries(GameInstanceManifest manifest) {
        List<Library> libraries = new ArrayList<>();
        SimpleMultimap<String, Integer, List<Integer>> indexes =
                new SimpleMultimap<>(HashMap::new, ArrayList::new);

        for (Library library : manifest.getLibraries()) {
            String id = library.groupId() + ":" + library.artifactId();
            VersionNumber version = VersionNumber.asVersion(library.version());

            if (!indexes.containsKey(id)) {
                indexes.put(id, libraries.size());
                libraries.add(library);
                continue;
            }

            boolean duplicate = false;
            for (int otherIndex : indexes.get(id)) {
                Library other = libraries.get(otherIndex);
                if (!CompatibilityRule.equals(library.rules(), other.rules())) {
                    continue;
                }

                int comparison = version.compareTo(VersionNumber.asVersion(other.version()));
                if (comparison > 0) {
                    libraries.set(otherIndex, library);
                } else if (comparison == 0 && library.equals(other)) {
                    String otherSerialized = JsonUtils.GSON.toJson(other);
                    String serialized = JsonUtils.GSON.toJson(library);
                    if (serialized.length() > otherSerialized.length()) {
                        libraries.set(otherIndex, library);
                    }
                } else if (comparison == 0) {
                    continue;
                }
                duplicate = true;
                break;
            }

            if (!duplicate) {
                indexes.put(id, libraries.size());
                libraries.add(library);
            }
        }

        return manifest.withLibraries(libraries);
    }
}
