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

    /// Rejects an exact requirement that cannot run in the selected instance.
    @Test
    public void rejectsIncompatibleRequiredVersion() {
        RemoteAddon.Version requiredVersion = version(RemoteAddon.Source.MODRINTH, "dependency", "wrong-game", Instant.EPOCH,
                RemoteAddon.VersionType.Release, List.of(), List.of("1.20"), List.of(loader(ModLoaderType.FORGE)));
        RemoteAddon.Version root = compatibleVersion("root", List.of(
                required(RemoteAddon.Source.MODRINTH, "dependency", requiredVersion.versionId())));
        FakeCatalog catalog = new FakeCatalog().add(mod(RemoteAddon.Source.MODRINTH, "dependency"), List.of(requiredVersion));

        assertThrows(IOException.class, () -> resolve(catalog, root));
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

    /// Rejects non-mod dependencies and dependencies without a compatible recommended version.
    @Test
    public void rejectsInvalidOrIncompatibleDependencies() {
        RemoteAddon.Version root = compatibleVersion("root", List.of(required(RemoteAddon.Source.CURSEFORGE, "dependency")));
        RemoteAddon.Version dependency = compatibleVersion("dependency", List.of());
        assertThrows(IOException.class, () -> resolve(new FakeCatalog()
                .add(addon(RemoteAddon.Source.CURSEFORGE, "dependency", RemoteAddon.Type.RESOURCE_PACK), List.of(dependency)), root));
        assertThrows(IOException.class, () -> resolve(new FakeCatalog()
                .add(mod(RemoteAddon.Source.CURSEFORGE, "dependency"), List.of()), root));
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
