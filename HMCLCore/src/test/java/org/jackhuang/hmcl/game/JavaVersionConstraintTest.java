// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Tests manifest precedence over the legacy Linux Java ceiling.
@NotNullByDefault
public final class JavaVersionConstraintTest {
    /// The affected Minecraft version.
    private static final GameVersionNumber GAME = GameVersionNumber.asGameVersion("1.7.10");
    /// Synthetic Java 8 metadata; no executable is launched.
    private static final JavaRuntime JAVA_8 = new JavaRuntime(Path.of("java8"),
            new JavaInfo(Platform.LINUX_X86_64, "1.8.0_402", "test"), false, true);
    /// Synthetic Java 25 metadata; no executable is launched.
    private static final JavaRuntime JAVA_25 = new JavaRuntime(Path.of("java25"),
            new JavaInfo(Platform.LINUX_X86_64, "25", "test"), false, true);

    /// Modern manifest requirements suppress the ceiling for both discovery and explicit runtime validation.
    @Test
    public void testModernRequirementsTakePrecedence() {
        assumeTrue(OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.X86_64);
        for (int major : List.of(17, 25)) {
            GameInstanceManifest manifest = new GameInstanceManifest(new GameInstanceID("modern"))
                    .withJavaVersion(new GameJavaVersion("test", major));
            GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, GAME);
            JavaRuntime runtime = new JavaRuntime(Path.of("java" + major),
                    new JavaInfo(Platform.LINUX_X86_64, String.valueOf(major), "test"), false, true);

            assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, manifest, runtime, analyzer));
            assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, manifest, null, analyzer));
            assertTrue(JavaVersionConstraint.GAME_JSON.appliesToVersion(GAME, manifest, runtime, analyzer));
            assertFalse(JavaVersionConstraint.GAME_JSON.checkJava(GAME, manifest, JAVA_8, analyzer));
            assertTrue(JavaVersionConstraint.GAME_JSON.checkJava(GAME, manifest, runtime, analyzer));
        }
    }

    /// Missing metadata and Java 8 declarations retain the platform-specific ceiling.
    @Test
    public void testLegacyRequirementsRetainCeiling() {
        GameInstanceManifest manifest = new GameInstanceManifest(new GameInstanceID("legacy"));
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, GAME);
        boolean affectedPlatform = OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.X86_64;
        for (int major : List.of(0, 8, -1)) {
            @Nullable GameInstanceManifest candidate = major == -1 ? null
                    : major == 0 ? manifest : manifest.withJavaVersion(GameJavaVersion.JAVA_8);
            assertEquals(affectedPlatform, JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, candidate, JAVA_25, analyzer));
            assertEquals(affectedPlatform, JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, candidate, null, analyzer));
        }
        assertTrue(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.checkJava(GAME, manifest, JAVA_8, analyzer));
        assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.checkJava(GAME, manifest, JAVA_25, analyzer));
    }

    /// Newer games, other runtime architectures, and Cleanroom remain exempt.
    @Test
    public void testExistingExemptions() {
        assumeTrue(OperatingSystem.CURRENT_OS == OperatingSystem.LINUX && Architecture.SYSTEM_ARCH == Architecture.X86_64);
        GameInstanceManifest manifest = new GameInstanceManifest(new GameInstanceID("legacy"));
        GameComponentAnalyzer analyzer = GameComponentAnalyzer.analyze(manifest, GAME);
        assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(
                GameVersionNumber.asGameVersion("1.13"), manifest, JAVA_25, analyzer));
        JavaRuntime armRuntime = new JavaRuntime(Path.of("java-arm"),
                new JavaInfo(Platform.LINUX_ARM64, "25", "test"), false, true);
        assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, manifest, armRuntime, analyzer));
        GameInstanceManifest cleanroom = manifest.withLibraries(List.of(new Library("com.cleanroommc", "cleanroom", "0.3.0")));
        GameComponentAnalyzer cleanroomAnalyzer = GameComponentAnalyzer.analyze(cleanroom, GAME);
        assertTrue(cleanroomAnalyzer.has(GameComponentType.CLEANROOM));
        assertFalse(JavaVersionConstraint.VANILLA_LINUX_JAVA_8.appliesToVersion(GAME, cleanroom, JAVA_25, cleanroomAnalyzer));
    }
}
