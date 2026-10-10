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
package org.jackhuang.hmcl.addon.mod;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// Immutable provider and dependency index for one completed mod-directory analysis.
///
/// Candidate selection only accepts reachable nested nodes, so inactive wrapper branches cannot
/// contribute providers or constraints.
@NotNullByDefault
public final class ModRelationIndex {
    /// Maximum convergence passes for nested candidate selection.
    private static final int MAX_SELECTION_PASSES = 32;

    /// Resolution state for one dependency declaration.
    public enum Status {
        /// An enabled provider satisfies the declared version constraint.
        SATISFIED,
        /// Matching providers exist but all are disabled with their host file.
        DISABLED,
        /// No provider exposes the requested ID.
        MISSING,
        /// Providers expose the ID but their known versions do not satisfy the constraint.
        VERSION_MISMATCH,
        /// A provider exists, but its version is unavailable or cannot be compared safely.
        UNKNOWN_VERSION
    }

    /// One capability provider retained in the immutable index.
    ///
    /// @param id the provided mod or alias ID
    /// @param version the provided version, or an empty string when unavailable
    /// @param host the top-level file controlling enable/disable state
    /// @param nestedSource the nested declaration source, or `null` for a top-level capability
    public record Provider(
            String id,
            String version,
            LocalModFile host,
            @Nullable NestedJarInspector.NestedJar nestedSource) {
        /// Returns whether this provider currently participates in the running instance.
        public boolean active() {
            return host.isActive();
        }
    }

    /// Result of resolving one dependency against all providers.
    ///
    /// @param dependency the declaration being resolved
    /// @param status the resulting state
    /// @param providers matching providers for display and diagnostics
    public record Resolution(
            ModDependency dependency,
            Status status,
            @Unmodifiable List<Provider> providers) {
        /// Creates an immutable resolution result.
        public Resolution {
            providers = List.copyOf(providers);
        }
    }

    /// One active hard or soft conflict and the providers that trigger it.
    public record ActiveConflict(
            LocalModFile declaringHost,
            @Nullable NestedJarInspector.NestedJar nestedSource,
            ModConflict conflict,
            @Unmodifiable List<Provider> providers) {
        /// Creates an immutable active conflict result.
        public ActiveConflict {
            providers = List.copyOf(providers);
        }
    }

    /// One unresolved required dependency declared by a top-level or selected nested mod.
    public record DependencyIssue(
            LocalModFile declaringHost,
            @Nullable NestedJarInspector.NestedJar nestedSource,
            Resolution resolution) {
    }

    /// Internal nested candidate with its host and tree parent.
    private record Candidate(
            NestedJarInspector.NestedJar node,
            LocalModFile host,
            @Nullable Candidate parent) {
    }

    /// Providers grouped by case-insensitive capability ID.
    private final @Unmodifiable Map<String, List<Provider>> providers;

    /// Active conflicts derived from the same selected provider graph.
    private final @Unmodifiable List<ActiveConflict> activeConflicts;

    /// Unresolved required dependencies derived from the selected provider graph.
    private final @Unmodifiable List<DependencyIssue> dependencyIssues;

    /// Complete immutable JIJ trees grouped by their top-level host file.
    private final @Unmodifiable Map<LocalModFile, List<NestedJarInspector.NestedJar>> bundledTrees;

    /// Selected runtime nested candidates grouped by top-level host file.
    private final @Unmodifiable Map<LocalModFile, Set<NestedJarInspector.NestedJar>> selectedNested;

    /// Whether every top-level file completed its bounded nested scan successfully.
    private final boolean complete;

    /// Creates an index for a completed manager scan.
    ///
    /// @param mods top-level local mod files
    /// @param minecraftVersion the instance Minecraft version used to filter wrapper candidates
    public ModRelationIndex(Collection<LocalModFile> mods, String minecraftVersion) {
        this(mods, minecraftVersion, EnumSet.allOf(ModLoaderType.class), true);
    }

    /// Creates an index for the loaders that can participate in the current instance.
    public ModRelationIndex(
            Collection<LocalModFile> mods,
            String minecraftVersion,
            Set<ModLoaderType> supportedLoaders,
            boolean complete) {
        Objects.requireNonNull(mods, "mods");
        this.complete = complete;
        Map<String, List<Provider>> mutableProviders = new LinkedHashMap<>();
        Map<LocalModFile, List<NestedJarInspector.NestedJar>> immutableTrees = new LinkedHashMap<>();
        List<Candidate> candidates = new ArrayList<>();

        for (LocalModFile mod : mods) {
            immutableTrees.put(mod, List.copyOf(mod.getBundledTree()));
            if (isModCompatible(mod, minecraftVersion, supportedLoaders)) {
                addProvider(mutableProviders, mod.getId(), mod.getVersion(), mod, null);
                mod.getProvidedVersions().forEach((id, version) ->
                        addProvider(mutableProviders, id, version, mod, null));
            }
            collectCandidates(
                    mod.getBundledTree(), mod, null, minecraftVersion, supportedLoaders, candidates);
        }

        List<LocalModFile> compatibleMods = mods.stream()
                .filter(mod -> isModCompatible(mod, minecraftVersion, supportedLoaders))
                .toList();
        Set<Candidate> selected = selectNestedCandidates(compatibleMods, candidates, true);
        Set<Candidate> selectedIncludingDisabled = selectNestedCandidates(
                compatibleMods, candidates, false);
        for (Candidate candidate : candidates) {
            if ((candidate.host().isActive() && !selected.contains(candidate))
                    || (!candidate.host().isActive() && !selectedIncludingDisabled.contains(candidate))) {
                continue;
            }
            NestedJarInspector.NestedJar node = candidate.node();
            addProvider(mutableProviders, node.id(), node.version(), candidate.host(), node);
            node.providedVersions().forEach((id, version) ->
                    addProvider(mutableProviders, id, version, candidate.host(), node));
        }

        Map<LocalModFile, Set<NestedJarInspector.NestedJar>> selectedByHost = new LinkedHashMap<>();
        for (Candidate candidate : selected) {
            selectedByHost.computeIfAbsent(candidate.host(), ignored -> new LinkedHashSet<>())
                    .add(candidate.node());
        }
        Map<LocalModFile, Set<NestedJarInspector.NestedJar>> immutableSelected = new LinkedHashMap<>();
        selectedByHost.forEach((host, nodes) -> immutableSelected.put(host, Set.copyOf(nodes)));
        this.selectedNested = Map.copyOf(immutableSelected);

        Map<String, List<Provider>> immutableProviders = new LinkedHashMap<>();
        mutableProviders.forEach((id, values) -> immutableProviders.put(id, List.copyOf(values)));
        this.providers = Map.copyOf(immutableProviders);
        this.bundledTrees = Map.copyOf(immutableTrees);

        List<ActiveConflict> conflicts = new ArrayList<>();
        for (LocalModFile mod : mods) {
            if (!mod.isActive() || !isModCompatible(mod, minecraftVersion, supportedLoaders)) {
                continue;
            }
            for (ModConflict conflict : mod.getConflicts()) {
                addActiveConflict(conflicts, mod, null, conflict);
            }
        }
        for (Candidate candidate : selected) {
            for (ModConflict conflict : candidate.node().conflicts()) {
                addActiveConflict(conflicts, candidate.host(), candidate.node(), conflict);
            }
        }
        this.activeConflicts = List.copyOf(conflicts);

        List<DependencyIssue> issues = new ArrayList<>();
        for (LocalModFile mod : mods) {
            if (mod.isActive() && isModCompatible(mod, minecraftVersion, supportedLoaders)) {
                addDependencyIssues(issues, mod, null, mod.getDependencies());
            }
        }
        for (Candidate candidate : selected) {
            addDependencyIssues(
                    issues,
                    candidate.host(),
                    candidate.node(),
                    candidate.node().dependencies());
        }
        this.dependencyIssues = List.copyOf(issues);
    }

    /// Returns providers for one mod ID or alias.
    ///
    /// @param id the requested capability ID
    /// @return immutable matching providers
    public @Unmodifiable List<Provider> getProviders(String id) {
        if (id == null) {
            return List.of();
        }
        return providers.getOrDefault(normalize(id), List.of());
    }

    /// Returns whether all nested scans completed without truncation or parse I/O failure.
    public boolean isComplete() {
        return complete;
    }

    /// Returns hard and soft conflicts active in this selected provider graph.
    public @Unmodifiable List<ActiveConflict> getActiveConflicts() {
        return activeConflicts;
    }

    /// Returns the complete immutable JIJ tree published for one top-level host.
    public @Unmodifiable List<NestedJarInspector.NestedJar> getBundledTree(LocalModFile host) {
        return bundledTrees.getOrDefault(host, List.of());
    }

    /// Returns every parsed nested capability ID published for one top-level host.
    public @Unmodifiable Set<String> getBundledIds(LocalModFile host) {
        Set<String> result = new LinkedHashSet<>();
        NestedJarInspector.collectIds(getBundledTree(host), result);
        return Set.copyOf(result);
    }

    /// Returns whether the candidate is selected in the active runtime provider graph.
    public boolean isNestedSelected(
            LocalModFile host,
            NestedJarInspector.NestedJar candidate) {
        return selectedNested.getOrDefault(host, Set.of()).contains(candidate);
    }

    /// Returns missing, disabled, incompatible, or unknown required dependencies.
    public @Unmodifiable List<DependencyIssue> getDependencyIssues() {
        return dependencyIssues;
    }

    /// Resolves one dependency declaration against enabled and disabled providers.
    ///
    /// @param dependency the dependency declaration
    /// @return the immutable resolution
    public Resolution resolve(ModDependency dependency) {
        List<Provider> matching = getProviders(dependency.id());
        if (matching.isEmpty()) {
            return new Resolution(dependency, Status.MISSING, matching);
        }

        List<Provider> active = matching.stream().filter(Provider::active).toList();
        if (active.isEmpty()) {
            return new Resolution(dependency, Status.DISABLED, matching);
        }
        if (dependency.versionConstraint().isBlank() || "*".equals(dependency.versionConstraint())) {
            return new Resolution(dependency, Status.SATISFIED, active);
        }

        boolean unknown = false;
        for (Provider provider : active) {
            if (provider.version().isBlank() || provider.version().contains("${")) {
                unknown = true;
            } else if (dependency.accepts(provider.version())) {
                return new Resolution(dependency, Status.SATISFIED, active);
            }
        }
        return new Resolution(
                dependency,
                unknown ? Status.UNKNOWN_VERSION : Status.VERSION_MISMATCH,
                active);
    }

    /// Returns active dependents that transitively lose their final satisfying provider.
    ///
    /// @param targets files that will be disabled or removed
    /// @param allMods all top-level mod files represented by this index
    /// @return dependents ordered by discovery
    public @Unmodifiable List<LocalModFile> findActiveDependents(
            Collection<LocalModFile> targets,
            Collection<LocalModFile> allMods) {
        Set<LocalModFile> doomed = new LinkedHashSet<>(targets);
        List<LocalModFile> dependents = new ArrayList<>();
        boolean changed;
        do {
            changed = false;
            for (LocalModFile candidate : allMods) {
                if (!candidate.isActive() || doomed.contains(candidate)) {
                    continue;
                }
                boolean broken = candidate.getDependencies().stream()
                        .filter(dependency -> !dependency.optional())
                        .anyMatch(dependency -> losesSatisfyingProvider(dependency, doomed));
                if (broken) {
                    doomed.add(candidate);
                    dependents.add(candidate);
                    changed = true;
                }
            }
        } while (changed);
        return List.copyOf(dependents);
    }

    /// Returns whether removing the doomed hosts eliminates every satisfying provider.
    private boolean losesSatisfyingProvider(ModDependency dependency, Set<LocalModFile> doomed) {
        List<Provider> remaining = getProviders(dependency.id()).stream()
                .filter(provider -> provider.active() && !doomed.contains(provider.host()))
                .toList();
        if (remaining.isEmpty()) {
            return true;
        }
        if (dependency.versionConstraint().isBlank() || "*".equals(dependency.versionConstraint())) {
            return false;
        }
        return remaining.stream().noneMatch(provider -> !provider.version().isBlank()
                && dependency.accepts(provider.version()));
    }

    /// Adds one conflict when at least one enabled provider matches its version constraint.
    private void addActiveConflict(
            List<ActiveConflict> result,
            LocalModFile declaringHost,
            @Nullable NestedJarInspector.NestedJar nestedSource,
            ModConflict conflict) {
        List<Provider> matching = getProviders(conflict.id()).stream()
                .filter(Provider::active)
                .filter(provider -> "*".equals(conflict.versionConstraint())
                        || !provider.version().isBlank() && conflict.matches(provider.version()))
                .toList();
        if (!matching.isEmpty()) {
            result.add(new ActiveConflict(declaringHost, nestedSource, conflict, matching));
        }
    }

    /// Adds unresolved non-optional dependency declarations to the issue list.
    private void addDependencyIssues(
            List<DependencyIssue> result,
            LocalModFile declaringHost,
            @Nullable NestedJarInspector.NestedJar nestedSource,
            Collection<ModDependency> dependencies) {
        for (ModDependency dependency : dependencies) {
            if (dependency.optional()) {
                continue;
            }
            Resolution resolution = resolve(dependency);
            if (resolution.status() != Status.SATISFIED) {
                result.add(new DependencyIssue(declaringHost, nestedSource, resolution));
            }
        }
    }

    /// Selects a stable set of reachable nested candidates under accumulated required constraints.
    private static Set<Candidate> selectNestedCandidates(
            Collection<LocalModFile> mods,
            List<Candidate> candidates,
            boolean activeOnly) {
        Map<String, List<Candidate>> groups = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            if ((!activeOnly || candidate.host().isActive()) && candidate.node().id() != null
                    && !candidate.node().id().isBlank()) {
                groups.computeIfAbsent(candidateGroupKey(candidate), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        Set<Candidate> selected = new LinkedHashSet<>();
        Set<String> seenStates = new HashSet<>();
        for (int pass = 0; pass < MAX_SELECTION_PASSES; pass++) {
            Set<Candidate> previousSelection = selected;
            Map<String, List<ModDependency>> constraints = new HashMap<>();
            for (LocalModFile mod : mods) {
                if (!activeOnly || mod.isActive()) {
                    addRequiredConstraints(constraints, mod.getDependencies());
                }
            }
            for (Candidate candidate : previousSelection) {
                addRequiredConstraints(constraints, candidate.node().dependencies());
            }

            Set<Candidate> next = new LinkedHashSet<>();
            for (Map.Entry<String, List<Candidate>> entry : groups.entrySet()) {
                List<ModDependency> idConstraints = constraints.getOrDefault(
                        normalize(entry.getValue().get(0).node().id()), List.of());
                @Nullable Candidate chosen = entry.getValue().stream()
                        .filter(candidate -> reachable(candidate, previousSelection))
                        .filter(candidate -> satisfiesAll(candidate, idConstraints))
                        .filter(candidate -> satisfiesJarJarRanges(candidate, entry.getValue()))
                        .max(candidateComparator())
                        .orElse(null);
                if (chosen != null) {
                    next.add(chosen);
                }
            }

            String state = next.stream()
                    .map(candidate -> candidate.host().getFile() + "!" + candidate.node().path()
                            + "@" + candidate.node().version())
                    .sorted()
                    .collect(Collectors.joining("|"));
            if (next.equals(previousSelection) || !seenStates.add(state)) {
                return next;
            }
            selected = next;
        }
        return selected;
    }

    /// Adds non-optional dependencies to a normalized constraint map.
    private static void addRequiredConstraints(
            Map<String, List<ModDependency>> constraints,
            Collection<ModDependency> dependencies) {
        for (ModDependency dependency : dependencies) {
            if (!dependency.optional()) {
                constraints.computeIfAbsent(normalize(dependency.id()), ignored -> new ArrayList<>())
                        .add(dependency);
            }
        }
    }

    /// Returns whether all ancestors are ungrouped or selected in the previous pass.
    private static boolean reachable(Candidate candidate, Set<Candidate> selected) {
        @Nullable Candidate parent = candidate.parent();
        while (parent != null) {
            @Nullable String parentId = parent.node().id();
            if (parentId != null && !parentId.isBlank() && !selected.contains(parent)) {
                return false;
            }
            parent = parent.parent();
        }
        return true;
    }

    /// Returns whether a candidate version satisfies every accumulated constraint for its ID.
    private static boolean satisfiesAll(Candidate candidate, Collection<ModDependency> constraints) {
        if (constraints.isEmpty()) {
            return true;
        }
        @Nullable String version = effectiveVersion(candidate.node());
        return version != null && !version.isBlank()
                && constraints.stream().allMatch(dependency -> dependency.accepts(version));
    }

    /// Returns whether a Forge candidate satisfies every wrapper range for its JarJar identifier.
    private static boolean satisfiesJarJarRanges(Candidate candidate, Collection<Candidate> group) {
        @Nullable String artifactVersion = candidate.node().jijArtifactVersion();
        if (artifactVersion == null || artifactVersion.isBlank()) {
            return group.stream().noneMatch(item -> item.node().jijVersionRange() != null
                    && !item.node().jijVersionRange().isBlank());
        }
        for (Candidate declaration : group) {
            @Nullable String range = declaration.node().jijVersionRange();
            if (range != null && !range.isBlank()
                    && !ModVersionPredicate.satisfies(ModLoaderType.FORGE, range, artifactVersion)) {
                return false;
            }
        }
        return true;
    }

    /// Orders nested candidates by loader-native version semantics and then stable path.
    private static Comparator<Candidate> candidateComparator() {
        return (left, right) -> {
            String leftVersion = Objects.requireNonNullElse(effectiveVersion(left.node()), "");
            String rightVersion = Objects.requireNonNullElse(effectiveVersion(right.node()), "");
            int comparison;
            if (left.node().loaderType() == ModLoaderType.FORGE
                    || left.node().loaderType() == ModLoaderType.NEO_FORGE
                    || left.node().loaderType() == ModLoaderType.CLEANROOM) {
                comparison = ModVersionPredicate.compareMaven(leftVersion, rightVersion);
            } else {
                comparison = ModVersionPredicate.compareSemVer(leftVersion, rightVersion);
            }
            return comparison != 0 ? comparison : left.node().path().compareTo(right.node().path());
        };
    }

    /// Returns the loader-selected version, preferring Forge JarJar's actual artifact version.
    private static @Nullable String effectiveVersion(NestedJarInspector.NestedJar node) {
        return node.jijArtifactVersion() != null && !node.jijArtifactVersion().isBlank()
                ? node.jijArtifactVersion()
                : node.version();
    }

    /// Returns the candidate grouping key used by Fabric ModId or Forge JarJar coordinates.
    private static String candidateGroupKey(Candidate candidate) {
        @Nullable String identifier = candidate.node().jijIdentifier();
        if (identifier != null && !identifier.isBlank()) {
            return "jarjar:" + identifier.toLowerCase(java.util.Locale.ROOT);
        }
        boolean maven = candidate.node().loaderType() == ModLoaderType.FORGE
                || candidate.node().loaderType() == ModLoaderType.NEO_FORGE
                || candidate.node().loaderType() == ModLoaderType.CLEANROOM;
        return (maven ? "maven:" : "semver:") + normalize(candidate.node().id());
    }

    /// Recursively collects MC-compatible nested candidates and their parent relationship.
    private static void collectCandidates(
            Collection<NestedJarInspector.NestedJar> nodes,
            LocalModFile host,
            @Nullable Candidate parent,
            String minecraftVersion,
            Set<ModLoaderType> supportedLoaders,
            List<Candidate> out) {
        for (NestedJarInspector.NestedJar node : nodes) {
            if (!isLoaderCompatible(node.loaderType(), supportedLoaders)) {
                continue;
            }
            boolean constrained = node.minecraftConstraintRequired()
                    && node.minecraftVersion() != null && !node.minecraftVersion().isBlank();
            if (constrained && minecraftVersion != null && !minecraftVersion.isBlank()
                    && !MinecraftVersionMatcher.matches(node, minecraftVersion)) {
                continue;
            }
            Candidate candidate = new Candidate(node, host, parent);
            out.add(candidate);
            collectCandidates(
                    node.children(), host, candidate, minecraftVersion, supportedLoaders, out);
        }
    }

    /// Returns whether a top-level or nested provider can load in the instance loader environment.
    private static boolean isLoaderCompatible(
            ModLoaderType loader,
            Set<ModLoaderType> supportedLoaders) {
        return loader == ModLoaderType.UNKNOWN || supportedLoaders.contains(loader);
    }

    /// Returns whether a top-level file matches both loader and required Minecraft constraints.
    private static boolean isModCompatible(
            LocalModFile mod,
            String minecraftVersion,
            Set<ModLoaderType> supportedLoaders) {
        if (!isLoaderCompatible(mod.getModLoaderType(), supportedLoaders)) {
            return false;
        }
        String constraint = mod.getGameVersion();
        return !mod.isMinecraftConstraintRequired() || constraint == null || constraint.isBlank()
                || minecraftVersion == null || minecraftVersion.isBlank()
                || MinecraftVersionMatcher.satisfies(
                mod.getModLoaderType(), constraint, minecraftVersion);
    }

    /// Adds one non-blank provider to a normalized mutable map.
    private static void addProvider(
            Map<String, List<Provider>> providers,
            @Nullable String id,
            @Nullable String version,
            LocalModFile host,
            @Nullable NestedJarInspector.NestedJar nestedSource) {
        if (id == null || id.isBlank()) {
            return;
        }
        Provider provider = new Provider(id, Objects.requireNonNullElse(version, ""), host, nestedSource);
        providers.computeIfAbsent(normalize(id), ignored -> new ArrayList<>()).add(provider);
    }

    /// Normalizes a case-insensitive mod ID lookup key.
    private static String normalize(String id) {
        return id.toLowerCase(java.util.Locale.ROOT);
    }
}
