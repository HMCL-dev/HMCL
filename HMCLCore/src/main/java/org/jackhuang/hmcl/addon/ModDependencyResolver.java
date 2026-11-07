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
import java.util.HashMap;
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
        return new Solver(catalog, rootVersion, gameVersion, targetLoaders).solve();
    }

    /// Contains the dependencies to download and incompatible dependencies that require manual installation.
    public record ResolutionResult(@Unmodifiable List<RemoteAddon.Version> versions,
                                   @Unmodifiable List<SkippedDependency> skippedDependencies) {
    }

    /// Describes a required dependency skipped because it has no compatible version for the instance.
    public record SkippedDependency(RemoteAddon.Source source, String projectId, @Nullable String versionId, String title) {
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

    /// Solves exact-version constraints with chronological candidate ordering and iterative backtracking.
    private static final class Solver {
        private final Catalog catalog;
        private final ProjectKey rootKey;
        private final String gameVersion;
        private final Set<ModLoaderType> targetLoaders;
        private final Map<ProjectKey, RemoteAddon> addonCache = new HashMap<>();
        private final Map<VersionKey, Optional<RemoteAddon.Version>> exactVersionCache = new HashMap<>();
        private final Map<ProjectKey, List<RemoteAddon.Version>> compatibleVersionCache = new HashMap<>();
        private final ArrayDeque<Choice> choices = new ArrayDeque<>();
        private SearchState state;

        /// Creates a solver with the root version fixed by the user's explicit selection.
        private Solver(Catalog catalog, RemoteAddon.Version rootVersion, String gameVersion, Set<ModLoaderType> targetLoaders) {
            this.catalog = catalog;
            this.rootKey = new ProjectKey(rootVersion.source(), rootVersion.projectId());
            this.gameVersion = gameVersion;
            this.targetLoaders = targetLoaders;
            this.state = new SearchState(rootVersion);
        }

        /// Searches until all reachable requirements are satisfied or every alternative has been exhausted.
        private ResolutionResult solve() throws IOException {
            while (true) {
                @Nullable String conflict = advance();
                if (conflict == null) {
                    List<RemoteAddon.Version> dependencies = state.selected.entrySet().stream()
                            .filter(entry -> !entry.getKey().equals(rootKey))
                            .map(Map.Entry::getValue)
                            .toList();
                    return new ResolutionResult(dependencies, List.copyOf(state.skipped.values()));
                }
                if (!backtrack()) {
                    throw new IOException("Unable to resolve required mod dependencies: " + conflict);
                }
            }
        }

        /// Propagates dependencies and selects candidates until reaching a solution or a constraint conflict.
        private @Nullable String advance() throws IOException {
            while (true) {
                if (!state.pendingVersions.isEmpty()) {
                    RemoteAddon.Version current = state.pendingVersions.removeFirst();
                    for (RemoteAddon.Dependency dependency : current.dependencies()) {
                        if (dependency.getType() == RemoteAddon.DependencyType.BROKEN) {
                            return "Broken dependency declared by " + describe(current);
                        }
                        if (dependency.getType() != RemoteAddon.DependencyType.REQUIRED) {
                            continue;
                        }
                        @Nullable String conflict = addRequirement(dependency, current);
                        if (conflict != null) {
                            return conflict;
                        }
                    }
                    continue;
                }

                @Nullable ProjectKey key = state.requiredProjects.pollFirst();
                if (key == null) {
                    return null;
                }
                if (state.selected.containsKey(key) || state.skipped.containsKey(key)) {
                    continue;
                }

                Requirements requirements = state.requirements.get(key);
                if (requirements.exactRequirements.size() > 1) {
                    return "Conflicting versions " + requirements.exactRequirements.keySet()
                            + " of required dependency " + key;
                }

                RemoteAddon addon = loadAddon(key, requirements);
                if (addon == RemoteAddon.BROKEN) {
                    throw new IOException("Failed to resolve required dependency " + key);
                }
                if (addon.type() != RemoteAddon.Type.MOD) {
                    throw new IOException("Required dependency " + key + " is not a mod");
                }

                List<RemoteAddon.Version> candidates;
                if (requirements.exactRequirements.size() == 1) {
                    Map.Entry<String, ExactRequirement> exactRequirement = requirements.exactRequirements.entrySet().iterator().next();
                    String requiredVersionId = exactRequirement.getKey();
                    RemoteAddon.Version selected = loadExactVersion(exactRequirement.getValue().reference()).orElseThrow(() ->
                            new IOException("Required version " + requiredVersionId + " of dependency " + key + " was not found"));
                    if (!key.equals(new ProjectKey(selected.source(), selected.projectId()))) {
                        throw new IOException("Required version " + requiredVersionId + " does not belong to the declared project");
                    }
                    if (!isCompatible(selected, gameVersion, targetLoaders)) {
                        state.skip(key, new SkippedDependency(key.source(), key.projectId(), requiredVersionId, addon.title()));
                        continue;
                    }
                    candidates = List.of(selected);
                } else {
                    candidates = loadCompatibleVersions(key, requirements);
                    if (candidates.isEmpty()) {
                        state.skip(key, new SkippedDependency(key.source(), key.projectId(), null, addon.title()));
                        continue;
                    }
                }

                if (candidates.size() > 1) {
                    choices.addLast(new Choice(key, candidates, state.copy()));
                }
                state.select(key, candidates.get(0));
            }
        }

        /// Adds one active parent's requirement and detects a conflict with an already selected version.
        private @Nullable String addRequirement(RemoteAddon.Dependency dependency, RemoteAddon.Version requester) throws IOException {
            RemoteAddon.@Nullable Source source = dependency.getSource();
            @Nullable String projectId = dependency.getId();
            @Nullable String requiredVersionId = dependency.getVersionId();
            if (source == null || projectId == null && requiredVersionId == null) {
                throw new IOException("Unresolvable required dependency declared by " + describe(requester));
            }

            ProjectKey key;
            if (projectId != null) {
                key = new ProjectKey(source, projectId);
            } else {
                RemoteAddon.Version requiredVersion = loadExactVersion(dependency).orElseThrow(() ->
                        new IOException("Required version " + requiredVersionId + " declared by " + describe(requester) + " was not found"));
                if (requiredVersion.source() != source) {
                    throw new IOException("Required version " + requiredVersionId + " does not belong to the declared source");
                }
                key = new ProjectKey(requiredVersion.source(), requiredVersion.projectId());
            }

            @Nullable SkippedDependency skipped = state.skipped.get(key);
            if (skipped != null) {
                if (requiredVersionId == null || requiredVersionId.equals(skipped.versionId())) {
                    return null;
                }
                state.skipped.remove(key);
            }

            Requirements requirements = state.requirements.computeIfAbsent(key, ignored -> new Requirements(dependency));
            if (requiredVersionId != null) {
                requirements.exactRequirements.putIfAbsent(requiredVersionId, new ExactRequirement(dependency, requester));
            }

            RemoteAddon.@Nullable Version existing = state.selected.get(key);
            if (existing != null) {
                if (requiredVersionId != null && !requiredVersionId.equals(existing.versionId())) {
                    return "Version " + existing.versionId() + " of required dependency " + key
                            + " conflicts with version " + requiredVersionId + " declared by " + describe(requester);
                }
            } else {
                state.requiredProjects.addLast(key);
            }
            return null;
        }

        /// Restores the state before the most recent choice and tries its next compatible candidate.
        private boolean backtrack() {
            while (!choices.isEmpty()) {
                Choice choice = choices.getLast();
                int nextIndex = choice.nextIndex + 1;
                if (nextIndex >= choice.candidates.size()) {
                    choices.removeLast();
                    continue;
                }

                choice.nextIndex = nextIndex;
                state = choice.before.copy();
                state.select(choice.key, choice.candidates.get(nextIndex));
                return true;
            }
            return false;
        }

        /// Loads and caches a project using the first requirement that referenced it.
        private RemoteAddon loadAddon(ProjectKey key, Requirements requirements) throws IOException {
            RemoteAddon addon = addonCache.get(key);
            if (addon != null) {
                return addon;
            }

            RemoteAddon.Dependency reference = requirements.reference;
            RemoteAddon.@Nullable Version requiredVersion = null;
            if (requirements.exactRequirements.size() == 1) {
                reference = requirements.exactRequirements.values().iterator().next().reference();
                requiredVersion = loadExactVersion(reference).orElse(null);
            }
            addon = catalog.loadAddon(reference, requiredVersion);
            addonCache.put(key, addon);
            return addon;
        }

        /// Loads and caches an exact version requirement from its declaring repository.
        private Optional<RemoteAddon.Version> loadExactVersion(RemoteAddon.Dependency dependency) throws IOException {
            VersionKey key = new VersionKey(dependency.getSource(), dependency.getVersionId());
            Optional<RemoteAddon.Version> cached = exactVersionCache.get(key);
            if (cached != null) {
                return cached;
            }

            Optional<RemoteAddon.Version> loaded = catalog.loadVersion(dependency);
            exactVersionCache.put(key, loaded);
            return loaded;
        }

        /// Loads every compatible candidate, retaining newest-first order for recommendation and backtracking.
        private List<RemoteAddon.Version> loadCompatibleVersions(ProjectKey key, Requirements requirements) throws IOException {
            List<RemoteAddon.Version> cached = compatibleVersionCache.get(key);
            if (cached != null) {
                return cached;
            }

            RemoteAddon addon = loadAddon(key, requirements);
            List<RemoteAddon.Version> loaded;
            try (Stream<RemoteAddon.Version> versions = catalog.loadVersions(addon, key.source())) {
                loaded = versions
                        .filter(version -> key.equals(new ProjectKey(version.source(), version.projectId())))
                        .filter(version -> isCompatible(version, gameVersion, targetLoaders))
                        .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                        .toList();
            }
            compatibleVersionCache.put(key, loaded);
            return loaded;
        }
    }

    /// Contains all mutable state for one branch of the dependency search.
    private static final class SearchState {
        private final LinkedHashMap<ProjectKey, RemoteAddon.Version> selected;
        private final LinkedHashMap<ProjectKey, SkippedDependency> skipped;
        private final Map<ProjectKey, Requirements> requirements;
        private final ArrayDeque<RemoteAddon.Version> pendingVersions;
        private final ArrayDeque<ProjectKey> requiredProjects;

        /// Initializes a branch with the explicitly selected root version.
        private SearchState(RemoteAddon.Version rootVersion) {
            this.selected = new LinkedHashMap<>();
            this.skipped = new LinkedHashMap<>();
            this.requirements = new HashMap<>();
            this.pendingVersions = new ArrayDeque<>();
            this.requiredProjects = new ArrayDeque<>();
            select(new ProjectKey(rootVersion.source(), rootVersion.projectId()), rootVersion);
        }

        /// Copies a branch so that failed candidates cannot leak assignments into the next attempt.
        private SearchState(SearchState other) {
            this.selected = new LinkedHashMap<>(other.selected);
            this.skipped = new LinkedHashMap<>(other.skipped);
            this.requirements = new HashMap<>();
            other.requirements.forEach((key, value) -> this.requirements.put(key, value.copy()));
            this.pendingVersions = new ArrayDeque<>(other.pendingVersions);
            this.requiredProjects = new ArrayDeque<>(other.requiredProjects);
        }

        /// Creates an independent snapshot for backtracking.
        private SearchState copy() {
            return new SearchState(this);
        }

        /// Selects a version and schedules its own dependency declarations for propagation.
        private void select(ProjectKey key, RemoteAddon.Version version) {
            skipped.remove(key);
            selected.put(key, version);
            pendingVersions.addLast(version);
        }

        /// Marks an unsatisfiable compatibility requirement as skipped rather than failing the whole installation.
        private void skip(ProjectKey key, SkippedDependency dependency) {
            Requirements projectRequirements = requirements.get(key);
            if (projectRequirements != null) {
                projectRequirements.exactRequirements.clear();
            }
            skipped.put(key, dependency);
        }
    }

    /// Stores all exact version IDs currently required for a project by active parent versions.
    private static final class Requirements {
        private final RemoteAddon.Dependency reference;
        private final LinkedHashMap<String, ExactRequirement> exactRequirements = new LinkedHashMap<>();

        /// Creates requirements with a repository reference for subsequent metadata loading.
        private Requirements(RemoteAddon.Dependency reference) {
            this.reference = reference;
        }

        /// Copies the constraints independently of other search branches.
        private Requirements copy() {
            Requirements copy = new Requirements(reference);
            copy.exactRequirements.putAll(exactRequirements);
            return copy;
        }
    }

    /// Retains the repository reference and declaring parent for one exact version constraint.
    private record ExactRequirement(RemoteAddon.Dependency reference, RemoteAddon.Version requester) {
    }

    /// Saves an unexplored newest-first candidate list and the state to restore for each alternative.
    private static final class Choice {
        private final ProjectKey key;
        private final List<RemoteAddon.Version> candidates;
        private final SearchState before;
        private int nextIndex;

        /// Creates a choice whose first candidate is tried immediately.
        private Choice(ProjectKey key, List<RemoteAddon.Version> candidates, SearchState before) {
            this.key = key;
            this.candidates = candidates;
            this.before = before;
        }
    }

    /// Identifies a remote project without conflating equal IDs from different sources.
    private record ProjectKey(RemoteAddon.Source source, String projectId) {
    }

    /// Identifies an exact remote version without conflating equal IDs from different sources.
    private record VersionKey(RemoteAddon.Source source, String versionId) {
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
