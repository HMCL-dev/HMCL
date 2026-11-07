// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests mod recommendation and required dependency graph resolution without network access.
@NotNullByDefault
public final class ModDependencyResolverTest {
    private static final String GAME_VERSION = "1.20.1";

    /// Selects the newest compatible alpha even when older releases and newer incompatible versions exist.
    @Test
    public void recommendsNewestCompatibleVersionUsingExistingRules() {
        RemoteAddon.Version older = compatibleVersion("older", Instant.parse("2024-01-01T00:00:00Z"), RemoteAddon.VersionType.Release);
        RemoteAddon.Version newestCompatible = compatibleVersion("newest-compatible", Instant.parse("2024-02-01T00:00:00Z"), RemoteAddon.VersionType.Alpha);
        RemoteAddon.Version wrongGame = compatibleVersion("wrong-game", Instant.parse("2024-03-01T00:00:00Z"), RemoteAddon.VersionType.Release);
        wrongGame = version(RemoteAddon.Source.CURSEFORGE, "root", wrongGame.name(), wrongGame.datePublished(), wrongGame.versionType(),
                List.of(), List.of("1.20"), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version wrongLoader = version(RemoteAddon.Source.CURSEFORGE, "root", "wrong-loader",
                Instant.parse("2024-04-01T00:00:00Z"), RemoteAddon.VersionType.Release,
                List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FABRIC)));

        assertEquals(newestCompatible, ModDependencyResolver.findRecommendedModVersion(
                Stream.of(wrongLoader, older, wrongGame, newestCompatible), GAME_VERSION, Set.of(ModLoaderType.FORGE)).orElseThrow());
    }

    /// Falls back to an older version when the newest file does not support any instance loader.
    @Test
    public void fallsBackToOlderCompatibleLoader() {
        RemoteAddon.Version older = compatibleVersion("older", Instant.EPOCH, RemoteAddon.VersionType.Release);
        RemoteAddon.Version newer = version(RemoteAddon.Source.CURSEFORGE, "root", "newer", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FABRIC)));

        assertEquals(older, ModDependencyResolver.findRecommendedModVersion(
                Stream.of(older, newer), GAME_VERSION, Set.of(ModLoaderType.FORGE, ModLoaderType.NEO_FORGE)).orElseThrow());
        assertTrue(ModDependencyResolver.findRecommendedModVersion(
                Stream.of(older), GAME_VERSION, Set.of()).isEmpty());
    }

    /// Uses the exact dependency version declared by the parent instead of a newer recommended version.
    @Test
    public void honorsVersionRequiredByDeclaringMod() throws IOException {
        RemoteAddon.Version requiredVersion = version(RemoteAddon.Source.MODRINTH, "dependency", "required", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version newer = version(RemoteAddon.Source.MODRINTH, "dependency", "newer", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "dependency", requiredVersion.versionId())));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.MODRINTH, "dependency"), List.of(newer, requiredVersion));

        assertEquals(List.of(requiredVersion), resolve(catalog, root));
    }

    /// Uses the exact constraint's own repository reference after merging with a project-only requirement.
    @Test
    public void mergesProjectOnlyAndExactRequirementsBeforeSelection() throws IOException {
        RemoteAddon.Version requiredVersion = version(RemoteAddon.Source.MODRINTH, "shared", "required", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version newer = version(RemoteAddon.Source.MODRINTH, "shared", "newer", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version projectOnlyParent = version(RemoteAddon.Source.MODRINTH, "project-only-parent", "parent", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(required(RemoteAddon.Source.MODRINTH, "shared")),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version exactParent = version(RemoteAddon.Source.MODRINTH, "exact-parent", "parent", Instant.EPOCH,
                RemoteAddon.VersionType.Release,
                List.of(required(RemoteAddon.Source.MODRINTH, "shared", requiredVersion.versionId())),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "project-only-parent"),
                required(RemoteAddon.Source.MODRINTH, "exact-parent")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.MODRINTH, "shared"), List.of(newer, requiredVersion))
                .add(mod(RemoteAddon.Source.MODRINTH, "project-only-parent"), List.of(projectOnlyParent))
                .add(mod(RemoteAddon.Source.MODRINTH, "exact-parent"), List.of(exactParent));

        List<RemoteAddon.Version> resolved = resolve(catalog, root);
        assertEquals(requiredVersion, resolved.stream().filter(version -> version.projectId().equals("shared")).findFirst().orElseThrow());
    }

    /// Rechecks a newly declared exact version after an earlier incompatible requirement was skipped.
    @Test
    public void resolvesDifferentExactRequirementAfterSkippedVersion() throws IOException {
        RemoteAddon.Version incompatible = version(RemoteAddon.Source.MODRINTH, "shared", "incompatible", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of("1.20"), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version compatible = version(RemoteAddon.Source.MODRINTH, "shared", "compatible", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version parent = version(RemoteAddon.Source.MODRINTH, "parent", "parent", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(required(RemoteAddon.Source.MODRINTH, "shared", compatible.versionId())),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "shared", incompatible.versionId()),
                required(RemoteAddon.Source.MODRINTH, "parent")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.MODRINTH, "shared"), List.of(incompatible, compatible))
                .add(mod(RemoteAddon.Source.MODRINTH, "parent"), List.of(parent));

        ModDependencyResolver.ResolutionResult result = resolveResult(catalog, root);
        assertEquals(compatible, result.versions().stream().filter(version -> version.projectId().equals("shared")).findFirst().orElseThrow());
        assertTrue(result.skippedDependencies().isEmpty());
    }

    /// Uses a later exact reference after an unconstrained project was skipped for lack of compatible versions.
    @Test
    public void skipsExactRequirementAfterProjectOnlySkip() throws IOException {
        RemoteAddon.Version incompatible = version(RemoteAddon.Source.MODRINTH, "shared", "incompatible", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of("1.20"), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version parent = version(RemoteAddon.Source.MODRINTH, "parent", "parent", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(required(RemoteAddon.Source.MODRINTH, "shared", incompatible.versionId())),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "shared"), required(RemoteAddon.Source.MODRINTH, "parent")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.MODRINTH, "shared"), List.of(incompatible))
                .add(mod(RemoteAddon.Source.MODRINTH, "parent"), List.of(parent));

        ModDependencyResolver.ResolutionResult result = resolveResult(catalog, root);
        assertEquals(List.of("parent"), projectIds(result.versions()));
        assertEquals(incompatible.versionId(), result.skippedDependencies().get(0).versionId());
    }

    /// Resolves Modrinth dependencies that identify their requirement only by version ID.
    @Test
    public void resolvesVersionOnlyRequirement() throws IOException {
        RemoteAddon.Version requiredVersion = version(RemoteAddon.Source.MODRINTH, "dependency", "required", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, null, requiredVersion.versionId())));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.MODRINTH, "dependency"), List.of(requiredVersion));

        assertEquals(List.of(requiredVersion), resolve(catalog, root));
    }

    /// Skips an exact requirement that cannot run in the selected instance and reports it for manual installation.
    @Test
    public void skipsIncompatibleRequiredVersion() throws IOException {
        RemoteAddon.Version requiredVersion = version(RemoteAddon.Source.MODRINTH, "dependency", "wrong-game", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of("1.20"), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "dependency", requiredVersion.versionId())));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.MODRINTH, "dependency"), List.of(requiredVersion));

        ModDependencyResolver.ResolutionResult result = resolveResult(catalog, root);
        assertTrue(result.versions().isEmpty());
        assertEquals(requiredVersion.versionId(), result.skippedDependencies().get(0).versionId());
        assertEquals("dependency", result.skippedDependencies().get(0).projectId());
    }

    /// Rejects two parent mods that require different exact versions of one dependency.
    @Test
    public void rejectsConflictingRequiredVersions() {
        RemoteAddon.Version first = compatibleVersion("dependency", List.of());
        RemoteAddon.Version second = version(RemoteAddon.Source.CURSEFORGE, "dependency", "second", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "dependency", first.versionId()),
                required(RemoteAddon.Source.CURSEFORGE, "dependency", second.versionId())));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.CURSEFORGE, "dependency"), List.of(first, second));

        assertThrows(IOException.class, () -> resolve(catalog, root));
    }

    /// Backtracks an earlier recommendation when a later parent requires a different exact version.
    @Test
    public void backtracksRecommendedVersionAfterExactVersionConflict() throws IOException {
        RemoteAddon.Version newer = version(RemoteAddon.Source.CURSEFORGE, "shared", "newer", Instant.EPOCH.plusSeconds(2),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version older = version(RemoteAddon.Source.CURSEFORGE, "shared", "older", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version parent = compatibleVersion("parent", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared", older.versionId())));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared"), required(RemoteAddon.Source.CURSEFORGE, "parent")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.CURSEFORGE, "shared"), List.of(newer, older))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "parent"), List.of(parent));

        List<RemoteAddon.Version> resolved = resolve(catalog, root);
        assertEquals(older, resolved.stream().filter(version -> version.projectId().equals("shared")).findFirst().orElseThrow());
        assertEquals(List.of("shared", "parent"), projectIds(resolved));
    }

    /// Rejects an entire parent candidate when its transitive constraints conflict with another branch.
    @Test
    public void backtracksParentCandidateWithTransitiveConflict() throws IOException {
        RemoteAddon.Version sharedV1 = version(RemoteAddon.Source.CURSEFORGE, "shared", "v1", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version sharedV2 = version(RemoteAddon.Source.CURSEFORGE, "shared", "v2", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version oldParent = version(RemoteAddon.Source.CURSEFORGE, "parent", "old", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(required(RemoteAddon.Source.CURSEFORGE, "shared", sharedV1.versionId())),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version newParent = version(RemoteAddon.Source.CURSEFORGE, "parent", "new", Instant.EPOCH.plusSeconds(2),
                RemoteAddon.VersionType.Release, List.of(required(RemoteAddon.Source.CURSEFORGE, "shared", sharedV2.versionId())),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version sibling = compatibleVersion("sibling", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared", sharedV1.versionId())));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "parent"), required(RemoteAddon.Source.CURSEFORGE, "sibling")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.CURSEFORGE, "parent"), List.of(newParent, oldParent))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "sibling"), List.of(sibling))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "shared"), List.of(sharedV2, sharedV1));

        List<RemoteAddon.Version> resolved = resolve(catalog, root);
        assertEquals(List.of("old", "sibling", "v1"), resolved.stream().map(RemoteAddon.Version::name).toList());
    }

    /// Exhausts every recommended candidate before rejecting an unsatisfiable constraint graph.
    @Test
    public void rejectsGraphOnlyAfterExhaustingAllCandidates() {
        RemoteAddon.Version first = version(RemoteAddon.Source.CURSEFORGE, "shared", "first", Instant.EPOCH.plusSeconds(2),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version second = version(RemoteAddon.Source.CURSEFORGE, "shared", "second", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version firstParent = compatibleVersion("first-parent", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared", first.versionId())));
        RemoteAddon.Version secondParent = compatibleVersion("second-parent", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared", second.versionId())));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared"),
                required(RemoteAddon.Source.CURSEFORGE, "first-parent"),
                required(RemoteAddon.Source.CURSEFORGE, "second-parent")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.CURSEFORGE, "shared"), List.of(first, second))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "first-parent"), List.of(firstParent))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "second-parent"), List.of(secondParent));

        assertThrows(IOException.class, () -> resolve(catalog, root));
    }

    /// Tries an older candidate when the newest candidate contains broken dependency metadata.
    @Test
    public void backtracksCandidateWithBrokenDependencies() throws IOException {
        RemoteAddon.Version newer = version(RemoteAddon.Source.CURSEFORGE, "dependency", "newer", Instant.EPOCH.plusSeconds(1),
                RemoteAddon.VersionType.Release, List.of(RemoteAddon.Dependency.ofBroken()),
                List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version older = compatibleVersion("dependency", List.of());
        RemoteAddon.Version root = compatibleVersion("root", List.of(required(RemoteAddon.Source.CURSEFORGE, "dependency")));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.CURSEFORGE, "dependency"), List.of(newer, older));

        assertEquals(List.of(older), resolve(catalog, root));
    }

    /// Resolves a required chain while ignoring optional, embedded, and incompatible edges.
    @Test
    public void resolvesOnlyTransitiveRequiredDependencies() throws IOException {
        RemoteAddon.Version c = compatibleVersion("c", List.of());
        RemoteAddon.Version b = compatibleVersion("b", List.of(required(RemoteAddon.Source.CURSEFORGE, "c")));
        RemoteAddon.Version a = compatibleVersion("a", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "b"),
                dependency(RemoteAddon.DependencyType.OPTIONAL, RemoteAddon.Source.CURSEFORGE, "optional"),
                dependency(RemoteAddon.DependencyType.EMBEDDED, RemoteAddon.Source.CURSEFORGE, "embedded"),
                dependency(RemoteAddon.DependencyType.INCOMPATIBLE, RemoteAddon.Source.CURSEFORGE, "incompatible")));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.CURSEFORGE, "b"), List.of(b))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "c"), List.of(c));

        assertEquals(List.of("b", "c"), projectIds(resolve(catalog, a)));
        assertEquals(0, catalog.loadCount(RemoteAddon.Source.CURSEFORGE, "optional"));
    }

    /// Terminates on cycles and downloads a shared diamond dependency only once.
    @Test
    public void handlesCyclesAndDiamondDependencies() throws IOException {
        RemoteAddon.Version d = compatibleVersion("d", List.of(required(RemoteAddon.Source.CURSEFORGE, "a")));
        RemoteAddon.Version b = compatibleVersion("b", List.of(required(RemoteAddon.Source.CURSEFORGE, "d")));
        RemoteAddon.Version c = compatibleVersion("c", List.of(required(RemoteAddon.Source.CURSEFORGE, "d")));
        RemoteAddon.Version a = compatibleVersion("a", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "b"), required(RemoteAddon.Source.CURSEFORGE, "c")));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.CURSEFORGE, "b"), List.of(b))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "c"), List.of(c))
                .add(mod(RemoteAddon.Source.CURSEFORGE, "d"), List.of(d));

        assertEquals(List.of("b", "c", "d"), projectIds(resolve(catalog, a)));
        assertEquals(1, catalog.loadCount(RemoteAddon.Source.CURSEFORGE, "d"));
    }

    /// Keeps equal project IDs from different remote sources separate.
    @Test
    public void distinguishesDependencySources() throws IOException {
        RemoteAddon.Version curseForge = version(RemoteAddon.Source.CURSEFORGE, "shared", "cf", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version modrinth = version(RemoteAddon.Source.MODRINTH, "shared", "mr", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "shared"), required(RemoteAddon.Source.MODRINTH, "shared")));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.CURSEFORGE, "shared"), List.of(curseForge))
                .add(mod(RemoteAddon.Source.MODRINTH, "shared"), List.of(modrinth));

        assertEquals(List.of(RemoteAddon.Source.CURSEFORGE, RemoteAddon.Source.MODRINTH),
                resolve(catalog, root).stream().map(RemoteAddon.Version::source).toList());
    }

    /// Rejects dependency metadata that cannot guarantee a complete required installation.
    @Test
    public void rejectsBrokenDependencies() {
        RemoteAddon.Version brokenEntry = compatibleVersion("root", List.of(RemoteAddon.Dependency.ofBroken()));
        assertThrows(IOException.class, () -> resolve(new FakeCatalog(), brokenEntry));

        RemoteAddon.Version missingProject = compatibleVersion("root", List.of(required(RemoteAddon.Source.CURSEFORGE, "missing")));
        assertThrows(IOException.class, () -> resolve(new FakeCatalog(), missingProject));
    }

    /// Rejects non-mod dependencies because their project metadata is invalid for mod installation.
    @Test
    public void rejectsNonModDependencies() {
        RemoteAddon.Version root = compatibleVersion("root", List.of(required(RemoteAddon.Source.CURSEFORGE, "dependency")));
        RemoteAddon.Version dependency = compatibleVersion("dependency", List.of());
        assertThrows(IOException.class, () -> resolve(new FakeCatalog()
                .add(addon(RemoteAddon.Source.CURSEFORGE, "dependency", RemoteAddon.Type.RESOURCE_PACK), List.of(dependency)), root));
    }

    /// Skips an incompatible dependency while continuing to resolve other compatible dependencies.
    @Test
    public void skipsOnlyDependenciesWithoutCompatibleVersions() throws IOException {
        RemoteAddon.Version compatible = compatibleVersion("compatible", List.of());
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.CURSEFORGE, "incompatible"),
                required(RemoteAddon.Source.CURSEFORGE, "compatible")));
        FakeCatalog catalog = new FakeCatalog()
                .add(mod(RemoteAddon.Source.CURSEFORGE, "incompatible"), List.of())
                .add(mod(RemoteAddon.Source.CURSEFORGE, "compatible"), List.of(compatible));

        ModDependencyResolver.ResolutionResult result = resolveResult(catalog, root);
        assertEquals(List.of(compatible), result.versions());
        assertEquals(List.of("incompatible"), result.skippedDependencies().stream()
                .map(ModDependencyResolver.SkippedDependency::projectId).toList());
    }

    /// Resolves a deep chain iteratively rather than consuming the Java call stack.
    @Test
    public void handlesDeepDependencyChains() throws IOException {
        FakeCatalog catalog = new FakeCatalog();
        RemoteAddon.Version root = compatibleVersion("node-0", List.of(required(RemoteAddon.Source.CURSEFORGE, "node-1")));
        for (int i = 1; i <= 500; i++) {
            String id = "node-" + i;
            List<RemoteAddon.Dependency> dependencies = i == 500
                    ? List.of()
                    : List.of(required(RemoteAddon.Source.CURSEFORGE, "node-" + (i + 1)));
            catalog.add(mod(RemoteAddon.Source.CURSEFORGE, id), List.of(compatibleVersion(id, dependencies)));
        }

        assertEquals(500, resolve(catalog, root).size());
    }

    /// Runs the package-level resolver entry point with the standard test compatibility target.
    private static List<RemoteAddon.Version> resolve(FakeCatalog catalog, RemoteAddon.Version root) throws IOException {
        return resolveResult(catalog, root).versions();
    }

    /// Runs the resolver and retains warnings for assertions.
    private static ModDependencyResolver.ResolutionResult resolveResult(FakeCatalog catalog, RemoteAddon.Version root) throws IOException {
        return ModDependencyResolver.resolveRequiredDependencies(catalog, root, GAME_VERSION, Set.of(ModLoaderType.FORGE));
    }

    /// Extracts project IDs for order-sensitive assertions.
    private static List<String> projectIds(List<RemoteAddon.Version> versions) {
        return versions.stream().map(RemoteAddon.Version::projectId).toList();
    }

    /// Creates a required dependency reference.
    private static RemoteAddon.Dependency required(RemoteAddon.Source source, String id) {
        return dependency(RemoteAddon.DependencyType.REQUIRED, source, id);
    }

    /// Creates a required dependency reference with an optional exact version requirement.
    private static RemoteAddon.Dependency required(RemoteAddon.Source source, @Nullable String id, String versionId) {
        return RemoteAddon.Dependency.ofGeneral(RemoteAddon.DependencyType.REQUIRED, source, id, versionId);
    }

    /// Creates a dependency reference of the requested type.
    private static RemoteAddon.Dependency dependency(RemoteAddon.DependencyType type, RemoteAddon.Source source, String id) {
        return RemoteAddon.Dependency.ofGeneral(type, source, id);
    }

    /// Creates a compatible version with no dependencies.
    private static RemoteAddon.Version compatibleVersion(String id, Instant published, RemoteAddon.VersionType type) {
        return version(RemoteAddon.Source.CURSEFORGE, "root", id, published, type,
                List.of(), List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
    }

    /// Creates a compatible version with the given project ID and dependencies.
    private static RemoteAddon.Version compatibleVersion(String projectId, List<RemoteAddon.Dependency> dependencies) {
        return version(RemoteAddon.Source.CURSEFORGE, projectId, projectId, Instant.EPOCH, RemoteAddon.VersionType.Release,
                dependencies, List.of(GAME_VERSION), List.of(loader(ModLoaderType.FORGE)));
    }

    /// Creates a complete remote version value.
    private static RemoteAddon.Version version(RemoteAddon.Source source, String projectId, String name, Instant published,
                                               RemoteAddon.VersionType type, List<RemoteAddon.Dependency> dependencies,
                                               List<String> gameVersions, List<AddonLoader> loaders) {
        return new RemoteAddon.Version(source, projectId + "-" + name, projectId, name, name, published, type,
                new RemoteAddon.File(Map.of(), "https://example.invalid/" + name + ".jar", name + ".jar"),
                dependencies, gameVersions, loaders);
    }

    /// Creates a typed addon loader.
    private static AddonLoader loader(ModLoaderType type) {
        return new AddonLoader(type.displayName(), type);
    }

    /// Creates a mod project whose fake catalog key is its source-qualified ID.
    private static RemoteAddon mod(RemoteAddon.Source source, String id) {
        return addon(source, id, RemoteAddon.Type.MOD);
    }

    /// Creates a remote project for fake catalog lookups.
    private static RemoteAddon addon(RemoteAddon.Source source, String id, RemoteAddon.Type type) {
        return new RemoteAddon(key(source, id), "", id, "", List.of(), "", "", new RemoteAddon.IAddon() {
            @Override
            public List<RemoteAddon> loadDependencies(RemoteAddonRepository repo, DownloadProvider downloadProvider) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<RemoteAddon.Version> loadVersions(RemoteAddonRepository repo, DownloadProvider downloadProvider) {
                throw new UnsupportedOperationException();
            }
        }, type);
    }

    /// Builds a source-qualified fake catalog key.
    private static String key(RemoteAddon.Source source, String id) {
        return source + ":" + id;
    }

    /// In-memory catalog with project lookup counters.
    @NotNullByDefault
    private static final class FakeCatalog implements ModDependencyResolver.Catalog {
        private final Map<String, RemoteAddon> addons = new HashMap<>();
        private final Map<String, List<RemoteAddon.Version>> versions = new HashMap<>();
        private final Map<String, Integer> loadCounts = new HashMap<>();

        /// Registers a project and its available versions.
        private FakeCatalog add(RemoteAddon addon, List<RemoteAddon.Version> availableVersions) {
            addons.put(addon.slug(), addon);
            versions.put(addon.slug(), new ArrayList<>(availableVersions));
            return this;
        }

        /// Returns how often a project was loaded.
        private int loadCount(RemoteAddon.Source source, String id) {
            return loadCounts.getOrDefault(key(source, id), 0);
        }

        /// Resolves a fake project or returns the standard broken sentinel.
        @Override
        public RemoteAddon loadAddon(RemoteAddon.Dependency dependency, RemoteAddon.@Nullable Version requiredVersion) {
            String id = dependency.getId() != null ? dependency.getId() : requiredVersion.projectId();
            String key = key(dependency.getSource(), id);
            loadCounts.merge(key, 1, Integer::sum);
            return addons.getOrDefault(key, RemoteAddon.BROKEN);
        }

        /// Finds an exact required version in the fake project metadata.
        @Override
        public Optional<RemoteAddon.Version> loadVersion(RemoteAddon.Dependency dependency) {
            return versions.values().stream()
                    .flatMap(List::stream)
                    .filter(version -> version.source() == dependency.getSource())
                    .filter(version -> dependency.getId() == null || version.projectId().equals(dependency.getId()))
                    .filter(version -> version.versionId().equals(dependency.getVersionId()))
                    .findFirst();
        }

        /// Streams the registered versions of a fake project.
        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddon addon, RemoteAddon.Source source) {
            return versions.getOrDefault(addon.slug(), List.of()).stream();
        }
    }
}
