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

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Decides whether the Java runtime HMCL selected is newer than the version the game
/// instance expects.
///
/// This is a pure function: no IO, no UI, no settings access. That is what keeps it
/// decoupled from {@code JavaManager} (which picks a runtime) and from the launch flow
/// (which decides what to do about a deviation).
///
/// ### Why this does not hardcode version ranges
///
/// `JavaVersionConstraint` encodes "Minecraft 1.18-1.20.4 needs Java 17" as literal
/// ranges. Every new Minecraft release invalidates such a range, which is how the
/// existing entries drifted out of date and why only Forge is covered today.
///
/// This class instead asks the version JSON: [GameInstanceManifest#javaVersion()] is
/// Mojang's own declaration of the runtime requirement, shipped with the game. A new
/// Minecraft release is picked up with zero code changes.
public final class JavaCompatibilityEvaluator {

    private JavaCompatibilityEvaluator() {
    }

    /// The Java major versions Mojang publishes Minecraft runtimes for, ascending.
    ///
    /// Used as the ruler for "how many steps newer" a runtime is. Java major versions are
    /// not evenly spaced (8, 16, 17, 21, 25), so counting steps requires this list rather
    /// than plain arithmetic.
    ///
    /// Maintenance: append here when a new Java LTS becomes an official Minecraft runtime.
    /// This is the only place to update.
    private static final List<GameJavaVersion> KNOWN_RUNTIMES = List.of(
            GameJavaVersion.JAVA_8,
            GameJavaVersion.JAVA_16,
            GameJavaVersion.JAVA_17,
            GameJavaVersion.JAVA_21,
            GameJavaVersion.JAVA_25
    );

    /// How many steps newer than expected each loader is known to tolerate.
    ///
    /// Maintenance: adding a loader is one line; tuning one is one number. A slightly
    /// wrong number causes a spurious or missing warning, never a structural failure,
    /// which is exactly why this belongs in data rather than in logic.
    ///
    /// The [ModLoaderType] reference is stored directly so a rename breaks the build
    /// instead of failing silently at runtime.
    private enum LoaderTolerance {
        FABRIC(ModLoaderType.FABRIC, 1),
        QUILT(ModLoaderType.QUILT, 1),
        FORGE(ModLoaderType.FORGE, 1),
        NEO_FORGE(ModLoaderType.NEO_FORGE, 1),
        LEGACY_FABRIC(ModLoaderType.LEGACY_FABRIC, 0),
        LITE_LOADER(ModLoaderType.LITE_LOADER, 0),
        /// Cleanroom declares its own Java requirement, see [resolveTarget].
        CLEANROOM(ModLoaderType.CLEANROOM, 0);

        final ModLoaderType type;
        final int steps;

        LoaderTolerance(ModLoaderType type, int steps) {
            this.type = type;
            this.steps = steps;
        }
    }

    /// Vanilla carries the best forward-compatibility record, so it gets the widest window.
    private static final int VANILLA_TOLERANCE = 2;

    public static JavaCompatibility evaluate(
            @Nullable GameVersionNumber gameVersion,
            GameInstanceManifest manifest,
            JavaRuntime java,
            GameComponentAnalyzer analyzer) {

        GameJavaVersion target = resolveTarget(gameVersion, manifest, analyzer);
        if (target == null)
            return new JavaCompatibility(0, java.getParsedVersion(), JavaCompatibility.Level.OK);

        int targetMajor = target.majorVersion();
        int actualMajor = java.getParsedVersion();

        // Older than expected is already handled by the mandatory constraints
        // (GAME_JSON / VANILLA), so this evaluator only looks upward.
        if (actualMajor <= targetMajor)
            return new JavaCompatibility(targetMajor, actualMajor, JavaCompatibility.Level.OK);

        if (actualMajor <= upperBound(targetMajor, toleranceSteps(analyzer)))
            return new JavaCompatibility(targetMajor, actualMajor, JavaCompatibility.Level.OK);

        return new JavaCompatibility(targetMajor, actualMajor, JavaCompatibility.Level.NEWER_THAN_EXPECTED);
    }

    /// The Java version this instance is expected to run on.
    public static @Nullable GameJavaVersion resolveTarget(
            @Nullable GameVersionNumber gameVersion,
            GameInstanceManifest manifest,
            GameComponentAnalyzer analyzer) {

        // Cleanroom's Java requirement is independent of the game version.
        String cleanroomVersion = analyzer.getVersion(GameComponentType.CLEANROOM);
        if (cleanroomVersion != null)
            return GameJavaVersion.getCleanroomJavaVersion(cleanroomVersion);

        // Mojang's own declaration. Present for every release that ships a runtime
        // requirement, and authoritative for future ones.
        GameJavaVersion declared = manifest.javaVersion();
        if (declared != null)
            return declared;

        // Versions predating the field; fall back to the known minimum.
        return gameVersion != null ? GameJavaVersion.getMinimumJavaVersion(gameVersion) : null;
    }

    /// The narrowest tolerance among the loaders present. Being conservative here means
    /// risking a spurious warning rather than a missing one.
    private static int toleranceSteps(GameComponentAnalyzer analyzer) {
        int steps = VANILLA_TOLERANCE;
        boolean anyLoader = false;

        for (LoaderTolerance tolerance : LoaderTolerance.values()) {
            if (analyzer.has(tolerance.type)) {
                anyLoader = true;
                steps = Math.min(steps, tolerance.steps);
            }
        }

        return anyLoader ? steps : VANILLA_TOLERANCE;
    }

    /// Walks [KNOWN_RUNTIMES] forward from the expected version.
    ///
    /// A runtime newer than the last known entry is out of range and warns, so a future
    /// Java release is flagged until this list is extended — it never silently passes.
    private static int upperBound(int targetMajor, int steps) {
        int index = -1;
        for (int i = 0; i < KNOWN_RUNTIMES.size(); i++) {
            if (KNOWN_RUNTIMES.get(i).majorVersion() == targetMajor) {
                index = i;
                break;
            }
        }

        // Unknown target: compare numerically so we never stay silent.
        if (index < 0)
            return targetMajor;

        int bounded = Math.min(index + steps, KNOWN_RUNTIMES.size() - 1);
        return KNOWN_RUNTIMES.get(bounded).majorVersion();
    }
}
