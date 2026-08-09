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

import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default implementation of a repository index snapshot for [DefaultGameRepository].
///
/// A snapshot begins unsealed so package-private writers can populate it. [#seal()] freezes the
/// instance map; afterwards any mutating method throws. Repository write paths must [#clone()] a
/// published snapshot, edit the copy, and publish it with
/// [DefaultGameRepository#publishSnapshot(DefaultGameRepositorySnapshot)].
///
/// Once sealed, this object is exposed as a [GameRepositorySnapshot].
///
/// Mutation methods are package-private: only code in `org.jackhuang.hmcl.game` may assemble a
/// snapshot. Subclasses such as HMCL-specific snapshots may override [#newEmpty()] to preserve
/// concrete type through [#clone()], analogous to
/// [DefaultGameInstance#withNewSnapshot(DefaultGameRepositorySnapshot)].
@NotNullByDefault
public class DefaultGameRepositorySnapshot implements GameRepositorySnapshot {
    protected final DefaultGameRepository repository;
    protected final DefaultGameRepositoryLayout layout;
    private Map<GameInstanceID, DefaultGameInstance> instances;
    private boolean sealed;

    /// Creates an empty unsealed snapshot for building a new snapshot.
    ///
    /// @param repository the owning repository
    /// @param layout     the layout for this snapshot
    public DefaultGameRepositorySnapshot(DefaultGameRepository repository, DefaultGameRepositoryLayout layout) {
        this.repository = repository;
        this.layout = layout;
        this.instances = new TreeMap<>();
        this.sealed = false;
    }

    /// Creates an empty unsealed snapshot of the same concrete type as this snapshot.
    ///
    /// @return a new empty unsealed snapshot
    protected DefaultGameRepositorySnapshot newEmpty() {
        return new DefaultGameRepositorySnapshot(repository, layout);
    }

    /// Freezes this snapshot so its instance map can no longer be modified.
    void seal() {
        if (!sealed) {
            instances = Collections.unmodifiableMap(new TreeMap<>(instances));
            sealed = true;
        }
    }

    private void checkMutable() {
        if (sealed) {
            throw new IllegalStateException("Snapshot has been published and cannot be modified");
        }
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return layout;
    }

    /// Returns the instance with the given id.
    ///
    /// @param id the instance id
    /// @return the instance, or `null` when absent
    public @Nullable DefaultGameInstance get(GameInstanceID id) {
        return instances.get(id);
    }

    /// Returns the registered instance with the given id.
    ///
    /// @param id the instance id
    /// @return the registered instance
    /// @throws NoSuchGameInstanceException if the instance is absent
    public DefaultGameInstance getRegistered(GameInstanceID id) throws NoSuchGameInstanceException {
        DefaultGameInstance instance = instances.get(id);
        if (instance != null) {
            return instance;
        }
        throw new NoSuchGameInstanceException(id);
    }

    /// {@inheritDoc}
    @Override
    public boolean hasInstance(GameInstanceID instanceId) {
        return instances.containsKey(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getRegistered(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public @Nullable DefaultGameInstance findInstance(GameInstanceID instanceId) {
        return instances.get(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public int getInstanceCount() {
        return instances.size();
    }

    /// {@inheritDoc}
    @Override
    public Collection<? extends DefaultGameInstance> getInstances() {
        return List.copyOf(instances.values());
    }

    /// {@inheritDoc}
    @Override
    public Collection<GameInstanceManifest> getInstanceManifests() {
        return instances.values().stream()
                .map(instance -> instance.manifest)
                .toList();
    }

    /// Returns a view of all instances in this snapshot.
    ///
    /// @return the instances; unmodifiable after [#seal()]
    public Collection<DefaultGameInstance> values() {
        return instances.values();
    }

    /// Returns an unmodifiable map view after seal, or the live map while building.
    ///
    /// @return the instance map
    public Map<GameInstanceID, DefaultGameInstance> asMap() {
        return instances;
    }

    /// Adds or replaces an instance in this unsealed snapshot.
    ///
    /// @param instance the instance bound to this snapshot
    void put(DefaultGameInstance instance) {
        checkMutable();
        instances.put(instance.getId(), instance);
    }

    /// Adds or replaces all instances from the given map.
    ///
    /// @param map instances keyed by id
    void putAll(Map<GameInstanceID, DefaultGameInstance> map) {
        checkMutable();
        instances.putAll(map);
    }

    /// Removes the instance with the given id.
    ///
    /// @param id the instance id
    void remove(GameInstanceID id) {
        checkMutable();
        instances.remove(id);
    }

    /// Removes all instances from this unsealed snapshot.
    void clear() {
        checkMutable();
        instances.clear();
    }

    /// Creates an unsealed copy of this snapshot with instances rebound to the copy.
    ///
    /// @return a mutable snapshot ready for further edits before publish
    @Override
    public DefaultGameRepositorySnapshot clone() {
        DefaultGameRepositorySnapshot newSnapshot = newEmpty();
        for (DefaultGameInstance instance : instances.values()) {
            newSnapshot.put(instance.withNewSnapshot(newSnapshot));
        }
        return newSnapshot;
    }

    /// Resolves official-layout inheritance and patches, then deduplicates launch libraries.
    ///
    /// Loader-specific argument repairs are applied later for a concrete launch attempt (for example
    /// by [LaunchManifestNormalizer#repairForLaunch(GameInstanceManifest)]).
    ///
    /// @param manifest the manifest to resolve
    /// @return the resolved manifest views
    /// @throws NoSuchGameInstanceException if an inherited parent is missing from this snapshot
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
        GameInstanceManifest.Resolved resolved = resolve(manifest, new HashSet<>());
        GameInstanceManifest launchManifest = uniqueLibraries(resolved.launchManifest());
        if (launchManifest != resolved.launchManifest()) {
            resolved = new GameInstanceManifest.Resolved(
                    resolved.unresolved(),
                    launchManifest,
                    resolved.standaloneManifest());
        }
        return resolved;
    }

    /// Resolves official-layout inheritance and patches without launch-library deduplication.
    ///
    /// @param manifest      the manifest to resolve
    /// @param resolvedSoFar instance ids already visited in the inheritance chain
    /// @return the resolved manifest views
    /// @throws NoSuchGameInstanceException if an inherited parent is missing from this snapshot
    private GameInstanceManifest.Resolved resolve(
            GameInstanceManifest manifest,
            Set<GameInstanceID> resolvedSoFar) throws NoSuchGameInstanceException {
        GameInstanceManifest launchManifest;
        GameInstanceManifest standaloneManifest = manifest.isRoot()
                ? manifest
                : addPatches(
                addPatches(new GameInstanceManifest(manifest.id()), List.of(manifest.toPatch())),
                manifest.patches());

        if (manifest.inheritsFrom() == null) {
            if (manifest.isRoot()) {
                // TODO: Breaking change, require much testing on versions installed with external installer, other launchers, and all kinds of versions.
                launchManifest = manifest.patches() != null
                        ? new GameInstanceManifest(manifest.id()).withPatches(manifest.patches())
                        : manifest;
            } else {
                launchManifest = manifest;
            }
            launchManifest = launchManifest.withJar(manifest.jar() == null ? manifest.id() : manifest.jar());
        } else {
            // To maximize the compatibility.
            if (!resolvedSoFar.add(manifest.id())) {
                LOG.warning("Found circular dependency instances: " + resolvedSoFar);
                launchManifest = (manifest.jar() == null ? manifest.withJar(manifest.id()) : manifest)
                        .withInheritsFrom(null);
            } else {
                DefaultGameInstance parentInstance = instances.get(manifest.inheritsFrom());
                if (parentInstance == null) {
                    throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                }

                // It is supposed to auto-install a version in getVersion.
                GameInstanceManifest.Resolved parentResolved =
                        resolve(parentInstance.getManifest(), resolvedSoFar);
                launchManifest = manifest.merge(parentResolved.launchManifest());
                standaloneManifest = addPatches(
                        addPatches(parentResolved.standaloneManifest(), List.of(manifest.toPatch())),
                        manifest.patches());
            }
        }

        if (manifest.patches() != null && !manifest.patches().isEmpty()) {
            // Assume patches themselves do not have patches recursively.
            List<GameInstancePatch> sortedPatches = manifest.patches().stream()
                    .sorted(Comparator.comparing(GameInstancePatch::getPriority))
                    .toList();
            for (GameInstancePatch patch : sortedPatches) {
                launchManifest = patch.merge(launchManifest);
            }
        }

        launchManifest = launchManifest.withId(manifest.id()).withPatches(null);
        standaloneManifest = standaloneManifest.withId(manifest.id());
        if (launchManifest.jar() != null) {
            standaloneManifest = standaloneManifest.withJar(launchManifest.jar());
        }

        return new GameInstanceManifest.Resolved(manifest, launchManifest, standaloneManifest);
    }

    /// Removes redundant library declarations while retaining rule-distinct variants.
    ///
    /// When two libraries share the same `groupId:artifactId` and equal compatibility rules, the
    /// newer version wins. When versions are equal and the coordinate objects compare equal, the
    /// declaration with the longer serialized JSON is kept (more metadata is treated as richer).
    /// Equal id and version with unequal coordinate payloads (for example distinct `text2speech`
    /// library vs native entries) are both retained.
    private static GameInstanceManifest uniqueLibraries(GameInstanceManifest manifest) {
        List<Library> libraries = new ArrayList<>();
        SimpleMultimap<String, Integer, List<Integer>> indexes =
                new SimpleMultimap<>(HashMap::new, ArrayList::new);

        for (Library library : manifest.getLibraries()) {
            String id = library.groupId() + ":" + library.artifactId();

            if (!indexes.containsKey(id)) {
                indexes.put(id, libraries.size());
                libraries.add(library);
                continue;
            }

            boolean duplicate = false;
            for (int otherIndex : indexes.get(id)) {
                Library other = libraries.get(otherIndex);
                // Rules differ: keep both (platform-specific variants).
                if (Objects.hashCode(library.rules()) != Objects.hashCode(other.rules())) {
                    continue;
                }

                // Rules equal: drop the older version.
                int comparison = VersionNumber.compare(library.version(), other.version());
                if (comparison > 0) {
                    libraries.set(otherIndex, library);
                } else if (comparison == 0) {
                    // Same library id and version: collapse true duplicates.
                    if (library.equals(other)) {
                        String otherSerialized = JsonUtils.GSON.toJson(other);
                        String serialized = JsonUtils.GSON.toJson(library);
                        // Prefer the entry with more serialized metadata when coordinates equal.
                        if (serialized.length() > otherSerialized.length()) {
                            libraries.set(otherIndex, library);
                        }
                    } else {
                        // Same id/version but not equal (e.g. text2speech jar vs natives): keep both.
                        continue;
                    }
                }
                duplicate = true;
                break;
            }

            if (!duplicate) {
                indexes.put(id, libraries.size());
                libraries.add(library);
            }
        }

        return libraries.size() == manifest.getLibraries().size()
                ? manifest
                : manifest.withLibraries(libraries);
    }

    private static GameInstanceManifest addPatches(GameInstanceManifest manifest, @Nullable List<GameInstancePatch> additional) {
        if (additional == null || additional.isEmpty()) {
            return manifest;
        }

        Set<String> patchIds = new HashSet<>();
        for (GameInstancePatch patch : additional) {
            if (patch.id() != null) {
                patchIds.add(patch.id());
            }
        }

        List<GameInstancePatch> patches = new ArrayList<>();
        if (manifest.patches() != null) {
            for (GameInstancePatch patch : manifest.patches()) {
                if (patch.id() == null || !patchIds.contains(patch.id())) {
                    patches.add(patch);
                }
            }
        }
        patches.addAll(additional);
        return manifest.withPatches(patches);
    }
}
