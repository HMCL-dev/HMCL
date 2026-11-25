// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests which library declarations survive launch manifest normalization.
@NotNullByDefault
public final class LaunchManifestNormalizerTest {

    /// The OSHI dependency shipped with Minecraft 1.12.2.
    private static final Library LEGACY_OSHI = new Library("oshi-project", "oshi-core", "1.1");

    /// The shared interfaces in the split OSHI distribution.
    private static final Library OSHI_COMMON = new Library("com.github.oshi", "oshi-common", "7.3.2");

    /// The FFM backend for the same OSHI release.
    private static final Library OSHI_FFM = new Library("com.github.oshi", "oshi-core-ffm", "7.3.2");

    /// Returns the launch libraries for an independent instance containing the given declarations.
    private static @Unmodifiable List<Library> normalize(List<Library> libraries) {
        var manifest = new GameInstanceManifest(new GameInstanceID("instance")).withLibraries(libraries);
        return LaunchManifestNormalizer.repairForLaunch(manifest).getLibraries();
    }

    /// Recognizes the OSHI group change and both backends of the split distribution.
    @Test
    public void testOshiCoordinateMigration() {
        Library monolithic = new Library("com.github.oshi", "oshi-core", "6.11.0");
        assertEquals(List.of(monolithic), normalize(List.of(LEGACY_OSHI, monolithic)));

        Library core = new Library("com.github.oshi", "oshi-core", "6.12.0");
        Library common = new Library("com.github.oshi", "oshi-common", "6.12.0");
        assertEquals(List.of(core, common), normalize(List.of(LEGACY_OSHI, core, common)));

        assertEquals(List.of(OSHI_COMMON), normalize(List.of(LEGACY_OSHI, OSHI_COMMON)));
        Library unrelated = new Library("example", "oshi-core", "9");
        assertEquals(List.of(OSHI_FFM, unrelated, OSHI_COMMON),
                normalize(List.of(LEGACY_OSHI, OSHI_FFM, unrelated, OSHI_COMMON)));
        assertEquals(List.of(OSHI_COMMON, unrelated, OSHI_FFM),
                normalize(List.of(OSHI_COMMON, unrelated, OSHI_FFM, LEGACY_OSHI)));
        Library jna = new Library("com.github.oshi", "oshi-core", OSHI_COMMON.version());
        assertEquals(List.of(OSHI_FFM, jna, OSHI_COMMON), normalize(List.of(OSHI_FFM, LEGACY_OSHI, jna, OSHI_COMMON)));
    }

    /// Retains the legacy JAR when the current platform lacks a successor providing the shared API.
    @Test
    public void testKeepOshiWithoutApiProvider() {
        Library disabled = new Library(OSHI_COMMON.artifact(), null, null, null, null, null,
                List.of(new CompatibilityRule(CompatibilityRule.Action.DISALLOW, null)), null, null);
        Library oldCore = new Library("com.github.oshi", "oshi-core", "6.11.0");
        Library splitCore = new Library("com.github.oshi", "oshi-core", "6.12.0");
        assertEquals(List.of(LEGACY_OSHI, splitCore), normalize(List.of(LEGACY_OSHI, oldCore, splitCore)));
        for (var libraries : List.of(
                List.of(LEGACY_OSHI),
                List.of(LEGACY_OSHI, new Library("example", "oshi-core", "6.11.0")),
                List.of(LEGACY_OSHI, splitCore),
                List.of(LEGACY_OSHI, OSHI_FFM),
                List.of(LEGACY_OSHI, OSHI_FFM, disabled),
                List.of(LEGACY_OSHI, OSHI_FFM, OSHI_COMMON.setClassifier("sources")))) {
            assertEquals(libraries, normalize(libraries));
        }
    }

    /// Recognizes the JNA platform artifact rename only when the successor can replace the old version.
    @Test
    public void testJnaPlatformCoordinateMigration() {
        Library old = new Library("net.java.dev.jna", "platform", "3.4.0");
        Library successor = new Library("net.java.dev.jna", "jna-platform", "5.19.1");
        Library older = new Library("net.java.dev.jna", "jna-platform", "3.3.0");
        assertEquals(List.of(successor), normalize(List.of(old, successor)));
        assertEquals(List.of(successor), normalize(List.of(successor, old)));
        assertEquals(List.of(old, older), normalize(List.of(old, older)));
    }

    /// Resolves versions within each classifier and extension independently.
    @Test
    public void testClassifierAndExtensionHaveIndependentVersions() {
        Library main = new Library("example", "library", "1");
        Library oldTests = new Library("example", "library", "2", "tests");
        Library archive = new Library(new Artifact("example", "library", "3", null, "zip"));
        Library newTests = new Library("example", "library", "4", "tests");

        assertEquals(List.of(main, newTests, archive), normalize(List.of(main, oldTests, archive, newTests)));
    }
}
