// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
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
    public static @Unmodifiable List<RemoteAddon.Version> resolveRequiredDependencies(
            DownloadProvider downloadProvider,
            RemoteAddon.Version rootVersion,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) throws IOException {
        return resolveRequiredDependencies(new RemoteCatalog(downloadProvider), rootVersion, gameVersion, targetLoaders);
    }

    /// Resolves required dependencies through the given catalog, keeping network access outside the graph algorithm.
    static @Unmodifiable List<RemoteAddon.Version> resolveRequiredDependencies(
            Catalog catalog,
            RemoteAddon.Version rootVersion,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) throws IOException {
        Set<ProjectKey> visited = new HashSet<>();
        visited.add(new ProjectKey(rootVersion.source(), rootVersion.projectId()));

        ArrayDeque<RemoteAddon.Version> pending = new ArrayDeque<>();
        pending.add(rootVersion);
        Map<ProjectKey, RemoteAddon.Version> resolved = new LinkedHashMap<>();

        while (!pending.isEmpty()) {
            RemoteAddon.Version current = pending.removeFirst();
            for (RemoteAddon.Dependency dependency : current.dependencies()) {
                if (dependency.getType() == RemoteAddon.DependencyType.BROKEN) {
                    throw new IOException("Broken dependency declared by " + describe(current));
                }
                if (dependency.getType() != RemoteAddon.DependencyType.REQUIRED) {
                    continue;
                }

                RemoteAddon.Source source = dependency.getSource();
                String projectId = dependency.getId();
                if (source == null || projectId == null) {
                    throw new IOException("Unresolvable required dependency declared by " + describe(current));
                }

                ProjectKey key = new ProjectKey(source, projectId);
                if (!visited.add(key)) {
                    continue;
                }

                RemoteAddon addon = catalog.loadAddon(dependency);
                if (addon == RemoteAddon.BROKEN) {
                    throw new IOException("Failed to resolve required dependency " + key);
                }
                if (addon.type() != RemoteAddon.Type.MOD) {
                    throw new IOException("Required dependency " + key + " is not a mod");
                }

                RemoteAddon.Version selected;
                try (Stream<RemoteAddon.Version> versions = catalog.loadVersions(addon, source)) {
                    selected = findRecommendedModVersion(versions, gameVersion, targetLoaders)
                            .orElseThrow(() -> new IOException("No compatible version of required dependency " + key
                                    + " for Minecraft " + gameVersion + " and loaders " + targetLoaders));
                }

                resolved.put(key, selected);
                visited.add(new ProjectKey(selected.source(), selected.projectId()));
                pending.addLast(selected);
            }
        }

        return List.copyOf(resolved.values());
    }

    /// Finds the same default mod version as the download UI: newest publication for the exact game version and any target loader.
    public static Optional<RemoteAddon.Version> findRecommendedModVersion(
            Stream<RemoteAddon.Version> versions,
            String gameVersion,
            Set<ModLoaderType> targetLoaders) {
        return versions
                .filter(version -> version.gameVersions().contains(gameVersion))
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .filter(version -> version.loaders().stream().anyMatch(loader ->
                        loader.type() instanceof ModLoaderType modLoaderType && targetLoaders.contains(modLoaderType)))
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

        /// Loads the project referenced by a dependency.
        RemoteAddon loadAddon(RemoteAddon.Dependency dependency) throws IOException;

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
        public RemoteAddon loadAddon(RemoteAddon.Dependency dependency) throws IOException {
            return dependency.load(downloadProvider);
        }

        /// Loads versions through the repository associated with the dependency source.
        @Override
        public Stream<RemoteAddon.Version> loadVersions(RemoteAddon addon, RemoteAddon.Source source) throws IOException {
            return addon.data().loadVersions(source.getRepository(), downloadProvider);
        }
    }
}
