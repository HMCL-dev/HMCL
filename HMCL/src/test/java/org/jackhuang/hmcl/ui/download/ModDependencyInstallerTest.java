// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import org.jackhuang.hmcl.addon.ModDependencyResolver;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.util.DigestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies installed-dependency planning and exact remote-file hash matching.
class ModDependencyInstallerTest {

    @TempDir
    Path tempDir;

    /// Any active local file belonging to the mapped project satisfies an unconstrained requirement.
    @Test
    void skipsInstalledProjectWithoutExactVersion() throws IOException {
        RemoteAddon.Version dependency = dependency(dependencyFile("sha1", "different"));
        ModDependencyResolver.ResolutionResult resolution = resolution(dependency, Map.of());
        Path installedFile = fileWithContent("installed.jar", "installed");
        List<ModDependencyInstaller.LocalModSnapshot> installed = List.of(
                new ModDependencyInstaller.LocalModSnapshot("example", installedFile, "1.0", true)
        );

        ModDependencyInstaller.Plan plan = ModDependencyInstaller.plan(installed, resolution, ignored -> Set.of("example"));

        assertTrue(plan.dependenciesToDownload().isEmpty());
        assertTrue(plan.versionConflicts().isEmpty());
    }

    /// An exact requirement accepts the local file only when its hash matches the remote file.
    @Test
    void acceptsExactRequirementWhenFileHashMatches() throws IOException {
        Path installedFile = fileWithContent("installed.jar", "same");
        RemoteAddon.Version dependency = dependency(dependencyFile("sha1", DigestUtils.digestToString("SHA-1", installedFile)));
        ModDependencyResolver.ResolutionResult resolution = resolution(dependency,
                Map.of(dependency, dependency.versionId()));
        List<ModDependencyInstaller.LocalModSnapshot> installed = List.of(
                new ModDependencyInstaller.LocalModSnapshot("example", installedFile, "1.0", true)
        );

        ModDependencyInstaller.Plan plan = ModDependencyInstaller.plan(installed, resolution, ignored -> Set.of("example"));

        assertTrue(plan.dependenciesToDownload().isEmpty());
        assertTrue(plan.versionConflicts().isEmpty());
    }

    /// An exact requirement reports an installed project whose local file has different content.
    @Test
    void reportsExactRequirementWhenFileHashDiffers() throws IOException {
        Path installedFile = fileWithContent("installed.jar", "old");
        RemoteAddon.Version dependency = dependency(dependencyFile("sha1", DigestUtils.digestToString("SHA-1", fileWithContent("remote.jar", "new"))));
        ModDependencyResolver.ResolutionResult resolution = resolution(dependency,
                Map.of(dependency, dependency.versionId()));
        List<ModDependencyInstaller.LocalModSnapshot> installed = List.of(
                new ModDependencyInstaller.LocalModSnapshot("example", installedFile, "1.0", true)
        );

        ModDependencyInstaller.Plan plan = ModDependencyInstaller.plan(installed, resolution, ignored -> Set.of("example"));

        assertTrue(plan.dependenciesToDownload().isEmpty());
        assertEquals(1, plan.versionConflicts().size());
        assertEquals(dependency, plan.versionConflicts().get(0).dependency());
    }

    /// Disabled local files do not satisfy a dependency requirement.
    @Test
    void ignoresDisabledInstalledMods() throws IOException {
        RemoteAddon.Version dependency = dependency(dependencyFile("sha1", "different"));
        ModDependencyResolver.ResolutionResult resolution = resolution(dependency, Map.of());
        List<ModDependencyInstaller.LocalModSnapshot> installed = List.of(
                new ModDependencyInstaller.LocalModSnapshot("example", fileWithContent("disabled.jar", "old"), "1.0", false)
        );

        ModDependencyInstaller.Plan plan = ModDependencyInstaller.plan(installed, resolution, ignored -> Set.of("example"));

        assertEquals(List.of(dependency), plan.dependenciesToDownload());
        assertTrue(plan.versionConflicts().isEmpty());
    }

    /// A project that cannot be mapped to local metadata remains downloadable.
    @Test
    void keepsUnknownProjectsDownloadable() throws IOException {
        RemoteAddon.Version dependency = dependency(dependencyFile("sha1", "different"));
        ModDependencyResolver.ResolutionResult resolution = resolution(dependency, Map.of());
        List<ModDependencyInstaller.LocalModSnapshot> installed = List.of(
                new ModDependencyInstaller.LocalModSnapshot("example", fileWithContent("installed.jar", "old"), "1.0", true)
        );

        ModDependencyInstaller.Plan plan = ModDependencyInstaller.plan(installed, resolution, ignored -> Set.of());

        assertEquals(List.of(dependency), plan.dependenciesToDownload());
        assertTrue(plan.versionConflicts().isEmpty());
    }

    /// Creates a local test file with deterministic content.
    private Path fileWithContent(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    /// Creates a dependency file carrying one published hash.
    private static RemoteAddon.File dependencyFile(String algorithm, String hash) {
        return new RemoteAddon.File(Map.of(algorithm, hash), "https://example.invalid/" + algorithm, "example.jar");
    }

    /// Creates a minimal resolved dependency version.
    private static RemoteAddon.Version dependency(RemoteAddon.File file) {
        return new RemoteAddon.Version(
                RemoteAddon.Source.MODRINTH,
                "version-1",
                "project-1",
                "Example",
                "1.0",
                Instant.EPOCH,
                RemoteAddon.VersionType.Release,
                file,
                List.of(),
                List.of("1.20.1"),
                List.of()
        );
    }

    /// Creates a resolution result with one selected dependency.
    private static ModDependencyResolver.ResolutionResult resolution(
            RemoteAddon.Version dependency, Map<RemoteAddon.Version, String> exactVersionIds) {
        return new ModDependencyResolver.ResolutionResult(
                List.of(dependency), List.of(), Map.of(dependency, RemoteAddon.BROKEN), exactVersionIds);
    }
}
