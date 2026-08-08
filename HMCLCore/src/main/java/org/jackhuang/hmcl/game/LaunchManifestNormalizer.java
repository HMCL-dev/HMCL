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

import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/// Launch-manifest library and argument adjustments used at resolve time and launch time.
///
/// Loader-specific argument repairs run later via
/// [#repairForLaunch(GameInstanceManifest)] (for example from `LauncherHelper`) and do not depend on
/// the installed filesystem. Path-sensitive BootstrapLauncher ignore-list fixes remain in
/// `DefaultLauncher`.
@NotNullByDefault
public final class LaunchManifestNormalizer {
    /// Prevents construction of this utility class.
    private LaunchManifestNormalizer() {
    }

    /// Applies loader-specific argument and library repairs for one launch attempt.
    ///
    /// Expects a structurally resolved launch manifest. Builds a single [GameComponentAnalyzer] for
    /// the whole repair. The input is unchanged.
    ///
    /// @param manifest the launch manifest to repair
    /// @return the repaired launch manifest
    /// @throws IllegalArgumentException if the manifest still contains inheritance or pending patches
    public static GameInstanceManifest repairForLaunch(GameInstanceManifest manifest) {
        if (manifest.inheritsFrom() != null || !manifest.getPatches().isEmpty()) {
            throw new IllegalArgumentException("Launch manifest must be structurally resolved");
        }

        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, null);
        GameInstanceManifest repaired = manifest;
        @Nullable String mainClass = repaired.mainClass();

        if (GameComponentAnalyzer.LAUNCH_WRAPPER_MAIN.equals(mainClass)) {
            // LaunchWrapper era (Forge/LiteLoader/OptiFine on 1.12 and earlier, and mixed stacks).
            repaired = repairLaunchWrapper(repaired, analyzer, true);
            if (GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(repaired.mainClass())) {
                // OptiFine + ModLauncher may promote mainClass off LaunchWrapper.
                repaired = repairModLauncher(repaired, analyzer);
            }
        } else if (GameComponentAnalyzer.MOD_LAUNCHER_MAIN.equals(mainClass)) {
            // Forge 1.13+ with OptiFine on ModLauncher.
            repaired = repairModLauncher(repaired, analyzer);
        } else if (GameComponentAnalyzer.BOOTSTRAP_LAUNCHER_MAIN.equals(mainClass)) {
            // Forge / NeoForge 1.17+ BootstrapLauncher ignore-list form that does not need the
            // installed filesystem (path-sensitive fixes for older BootstrapLauncher run in
            // DefaultLauncher when building the process command).
            repaired = repairBootstrapLauncher(repaired, analyzer);
        }
        // Vanilla and Fabric/Quilt need no loader-specific argument repair here.

        return removeLegacyLog4jPatch(repaired);
    }

    /// Repairs LaunchWrapper tweak-class configuration.
    ///
    /// Installing Forge can replace the full game argument list in the version JSON, which drops
    /// LiteLoader and OptiFine tweakers. Compatible tweak classes are re-inserted in deterministic
    /// order when still required.
    private static GameInstanceManifest repairLaunchWrapper(
            GameInstanceManifest manifest,
            GameComponentAnalyzer analyzer,
            boolean reorderTweakClass) {
        GameInstanceLibraryBuilder builder = new GameInstanceLibraryBuilder(manifest);
        @Nullable String mainClass = null;

        // Re-add LiteLoader tweaker when Forge overwrote the argument list (unless ModLauncher is in use).
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
                // Standalone OptiFine uses the plain OptiFine tweaker.
                if (builder.hasTweakClass(GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(1))) {
                    builder.replaceTweakClass(
                            GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(1),
                            GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(0),
                            !reorderTweakClass,
                            reorderTweakClass);
                }
            } else if (analyzer.hasModLauncher()) {
                // Prefer ModLauncher over LaunchWrapper when both are present.
                mainClass = GameComponentAnalyzer.MOD_LAUNCHER_MAIN;
                for (String optiFineTweaker : GameComponentAnalyzer.OPTIFINE_TWEAKERS) {
                    builder.removeTweakClass(optiFineTweaker);
                }
            } else if (builder.hasTweakClass(GameComponentAnalyzer.OPTIFINE_TWEAKERS.get(0))) {
                // With Forge or LiteLoader, OptiFine's Forge tweaker is required.
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

        GameInstanceManifest repaired = builder.build();
        return mainClass == null ? repaired : repaired.withMainClass(mainClass);
    }

    /// Adds the transformer discovery service required by Forge and OptiFine on ModLauncher.
    private static GameInstanceManifest repairModLauncher(
            GameInstanceManifest manifest,
            GameComponentAnalyzer analyzer) {
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
    /// BootstrapLauncher 0.1.17 and newer apply `ignoreList` only to the file name of each classpath
    /// entry, so it is enough to ensure the primary jar name is listed. Older versions match
    /// substrings against full paths and are repaired in `DefaultLauncher` using the launch-time
    /// library classpath.
    private static GameInstanceManifest repairBootstrapLauncher(
            GameInstanceManifest manifest,
            GameComponentAnalyzer analyzer) {
        // Fix wrong configurations when launching 1.17+ with Forge / NeoForge.
        if (!analyzer.has(GameComponentType.FORGE) && !analyzer.has(GameComponentType.NEO_FORGE)) {
            return manifest;
        }

        if (Optional.ofNullable(analyzer.getVersion(GameComponentType.BOOTSTRAP_LAUNCHER))
                .filter(version -> VersionNumber.compare(version, "0.1.17") >= 0)
                .isEmpty()) {
            return manifest;
        }

        // bootstraplauncher 0.1.17+ only applies ignoreList to classpath file names, so only the
        // primary jar name needs to be fixed here.
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
    /// HMCL once injected `log4j-patch` to mitigate the Log4j vulnerability. The launcher now
    /// rewrites `log4j2.xml` instead, so the leftover library entry is dropped.
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
}
