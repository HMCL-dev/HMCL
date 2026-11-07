// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/// Selects compatible mod versions and resolves the transitive closure of required dependencies.
@NotNullByDefault
public final class ModDependencyResolver {

    /// Resolves required dependencies using the remote repositories declared by each dependency.
    public static ResolutionResult resolveRequiredDependencies(
            DownloadProvider downloadProvider,
            RemoteAddon.Version rootVersion,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) throws IOException {
        return resolveRequiredDependencies(new RemoteCatalog(downloadProvider), rootVersion, gameVersion, targetLoaders);
    }

    /// Resolves required dependencies through the given catalog, keeping network access outside the graph algorithm.
    static ResolutionResult resolveRequiredDependencies(
            Catalog catalog,
            RemoteAddon.Version rootVersion,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) throws IOException {
        ProjectKey rootKey = new ProjectKey(rootVersion.source(), rootVersion.projectId());
        ArrayDeque<RemoteAddon.Version> pending = new ArrayDeque<>();
        pending.add(rootVersion);
        Map<ProjectKey, RemoteAddon.Version> resolved = new LinkedHashMap<>();
        Map<ProjectKey, SkippedDependency> skipped = new LinkedHashMap<>();

        while (!pending.isEmpty()) {
            RemoteAddon.Version current = pending.removeFirst();
            for (RemoteAddon.Dependency dependency : current.dependencies()) {
                if (dependency.getType() == RemoteAddon.DependencyType.BROKEN) {
                    throw new IOException("Broken dependency declared by " + describe(current));
                }
                if (dependency.getType() != RemoteAddon.DependencyType.REQUIRED) {
                    continue;
                }

                RemoteAddon.@Nullable Source source = dependency.getSource();
                @Nullable String projectId = dependency.getId();
                @Nullable String requiredVersionId = dependency.getVersionId();
                if (source == null || projectId == null && requiredVersionId == null) {
                    throw new IOException("Unresolvable required dependency declared by " + describe(current));
                }

                @Nullable ProjectKey key = projectId != null ? new ProjectKey(source, projectId) : null;
                if (key != null) {
                    @Nullable SkippedDependency skippedDependency = skipped.get(key);
                    if (skippedDependency != null
                            && (requiredVersionId == null || requiredVersionId.equals(skippedDependency.versionId()))) {
                        continue;
                    }
                    if (findExistingVersion(rootVersion, rootKey, resolved, key, requiredVersionId, current) != null) {
                        continue;
                    }
                }

                RemoteAddon.@Nullable Version selected = null;
                if (requiredVersionId != null) {
                    selected = catalog.loadVersion(dependency).orElseThrow(() ->
                            new IOException("Required version " + requiredVersionId + " declared by " + describe(current) + " was not found"));
                    if (selected.source() != source || projectId != null && !projectId.equals(selected.projectId())) {
                        throw new IOException("Required version " + requiredVersionId + " does not belong to the declared project");
                    }

                    key = new ProjectKey(selected.source(), selected.projectId());
                    @Nullable SkippedDependency skippedDependency = skipped.get(key);
                    if (skippedDependency != null && requiredVersionId.equals(skippedDependency.versionId())) {
                        continue;
                    }
                    if (findExistingVersion(rootVersion, rootKey, resolved, key, requiredVersionId, current) != null) {
                        continue;
                    }
                }

                RemoteAddon addon = catalog.loadAddon(dependency, selected);
                if (addon == RemoteAddon.BROKEN) {
                    throw new IOException("Failed to resolve required dependency " + key);
                }
                if (addon.type() != RemoteAddon.Type.MOD) {
                    throw new IOException("Required dependency " + key + " is not a mod");
                }

                if (selected == null) {
                    try (Stream<RemoteAddon.Version> versions = catalog.loadVersions(addon, source)) {
                        selected = findRecommendedModVersion(versions, gameVersion, targetLoaders).orElse(null);
                    }
                    if (selected == null) {
                        skipped.put(key, new SkippedDependency(source, key.projectId(), null, addon.title()));
                        continue;
                    }
                } else if (!isCompatible(selected, gameVersion, targetLoaders)) {
                    skipped.put(key, new SkippedDependency(source, key.projectId(), requiredVersionId, addon.title()));
                    continue;
                }

                skipped.remove(key);
                resolved.put(key, selected);
                pending.addLast(selected);
            }
        }

        return new ResolutionResult(List.copyOf(resolved.values()), List.copyOf(skipped.values()));
    }

    /// Contains the dependencies to download and incompatible dependencies that require manual installation.
    public record ResolutionResult(@Unmodifiable List<RemoteAddon.Version> versions,
                                   @Unmodifiable List<SkippedDependency> skippedDependencies) {
    }

    /// Describes a required dependency skipped because it has no compatible version for the instance.
    public record SkippedDependency(RemoteAddon.Source source, String projectId, @Nullable String versionId, String title) {
    }

    /// Returns an already selected version, rejecting a conflicting exact requirement from another mod.
    private static RemoteAddon.@Nullable Version findExistingVersion(
            RemoteAddon.Version rootVersion,
            ProjectKey rootKey,
            Map<ProjectKey, RemoteAddon.Version> resolved,
            ProjectKey key,
            @Nullable String requiredVersionId,
            RemoteAddon.Version requester) throws IOException {
        RemoteAddon.@Nullable Version existing = key.equals(rootKey) ? rootVersion : resolved.get(key);
        if (existing != null && requiredVersionId != null && !requiredVersionId.equals(existing.versionId())) {
            throw new IOException("Conflicting versions of required dependency " + key + " declared by " + describe(requester));
        }
        return existing;
    }

    /// Checks whether a version supports the exact game version and at least one target loader.
    private static boolean isCompatible(RemoteAddon.Version version, String gameVersion, Set<ModLoaderType> targetLoaders) {
        return version.gameVersions().contains(gameVersion) && version.loaders().stream().anyMatch(loader ->
                loader.type() instanceof ModLoaderType modLoaderType && targetLoaders.contains(modLoaderType));
    }

    /// Finds the same default mod version as the download UI: newest publication for the exact game version and any target loader.
    public static Optional<RemoteAddon.Version> findRecommendedModVersion(
            Stream<RemoteAddon.Version> versions,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) {
        return versions
                .filter(version -> isCompatible(version, gameVersion, targetLoaders))
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .findFirst();
    }

    /// Returns a source-qualified version name for diagnostics.
    private static String describe(RemoteAddon.Version version) {
        return version.source() + ":" + version.projectId() + " (" + version.name() + ")";
    }

    /// Prevents instantiation of this utility class.
    private ModDependencyResolver() {
    }

    /// Identifies a remote project without conflating equal IDs from different sources.
    private record ProjectKey(RemoteAddon.Source source, String projectId) {
    }

    /// Supplies project metadata and versions to the dependency graph algorithm.
    interface Catalog {

        /// Loads the project referenced by a dependency, reusing an already resolved version when possible.
        RemoteAddon loadAddon(RemoteAddon.Dependency dependency, RemoteAddon.@Nullable Version requiredVersion) throws IOException;

        /// Loads the exact version requested by a dependency.
        Optional<RemoteAddon.Version> loadVersion(RemoteAddon.Dependency dependency) throws IOException;

        /// Loads all versions of a resolved project from the dependency's source repository.
        Stream<RemoteAddon.Version> loadVersions(RemoteAddon addon, RemoteAddon.Source source) throws IOException;
    }

    /// Adapts the existing remote addon repositories to the resolver catalog.
    private static final class RemoteCatalog implements Catalog {
        private final DownloadProvider downloadProvider;

        /// Creates a catalog using the current download provider.
        private RemoteCatalog(DownloadProvider downloadProvider) {
            this.downloadProvider = downloadProvider;
        }

        /// Resolves a dependency through its own source repository.
        @Override
        public RemoteAddon loadAddon(RemoteAddon.Dependency dependency, RemoteAddon.@Nullable Version requiredVersion) throws IOException {
            if (dependency.getId() == null && requiredVersion != null) {
                return dependency.getSource().getRepository().resolveDependency(downloadProvider, requiredVersion.projectId());
            }
            return dependency.load(downloadProvider);
        }

        /// Resolves the exact version requested by a dependency.
        @Override
        public Optional<RemoteAddon.Version> loadVersion(RemoteAddon.Dependency dependency) throws IOException {
            return dependency.getSource().getRepository().getRemoteVersionById(downloadProvider, dependency.getVersionId());
        }

        /// Loads versions through the repository associated with the dependency source.
        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddon addon, RemoteAddon.Source source) throws IOException {
            return addon.data().loadVersions(source.getRepository(), downloadProvider);
        }
    }
}
