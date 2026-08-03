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

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default implementation of a repository index snapshot for [DefaultGameRepository].
///
/// A status begins unsealed so writers can populate it. [#seal()] freezes the instance map;
/// afterwards any mutating method throws. Callers must [#clone()] a published status, edit the
/// copy, and publish it with [DefaultGameRepository#publishStatus(DefaultGameRepositoryStatus)].
///
/// Once sealed, this object is exposed as a [GameRepositorySnapshot]. Provisional placeholders
/// remain reachable through [#get(GameInstanceID)] but are excluded from the public snapshot view.
///
/// Subclasses such as HMCL-specific statuses may override [#newEmpty()] to preserve concrete type
/// through [#clone()], analogous to [DefaultGameInstance#withNewStatus(DefaultGameRepositoryStatus)].
@NotNullByDefault
public class DefaultGameRepositoryStatus implements GameRepositorySnapshot {
    protected final DefaultGameRepository repository;
    protected final DefaultGameRepositoryLayout layout;
    private Map<GameInstanceID, DefaultGameInstance> instances;
    private boolean sealed;

    /// Creates an empty unsealed status for building a new snapshot.
    ///
    /// @param repository the owning repository
    /// @param layout     the layout for this snapshot
    public DefaultGameRepositoryStatus(DefaultGameRepository repository, DefaultGameRepositoryLayout layout) {
        this.repository = repository;
        this.layout = layout;
        this.instances = new TreeMap<>();
        this.sealed = false;
    }

    /// Creates an empty unsealed status of the same concrete type as this status.
    ///
    /// @return a new empty unsealed status
    protected DefaultGameRepositoryStatus newEmpty() {
        return new DefaultGameRepositoryStatus(repository, layout);
    }

    /// Freezes this status so its instance map can no longer be modified.
    public void seal() {
        if (!sealed) {
            instances = Collections.unmodifiableMap(new TreeMap<>(instances));
            sealed = true;
        }
    }

    /// Returns whether this status has been sealed.
    ///
    /// @return whether mutation is forbidden
    public boolean isSealed() {
        return sealed;
    }

    private void checkMutable() {
        if (sealed) {
            throw new IllegalStateException("Status has been published and cannot be modified");
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

    /// Returns the instance with the given id, including provisional placeholders.
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
    /// @throws NoSuchGameInstanceException if the instance is absent or provisional
    public DefaultGameInstance getRegistered(GameInstanceID id) throws NoSuchGameInstanceException {
        DefaultGameInstance instance = instances.get(id);
        if (instance != null && !instance.isProvisional()) {
            return instance;
        }
        throw new NoSuchGameInstanceException(id);
    }

    /// {@inheritDoc}
    @Override
    public boolean hasInstance(GameInstanceID instanceId) {
        DefaultGameInstance instance = instances.get(instanceId);
        return instance != null && !instance.isProvisional();
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        return getRegistered(instanceId);
    }

    /// {@inheritDoc}
    @Override
    public @Nullable DefaultGameInstance findInstance(GameInstanceID instanceId) {
        DefaultGameInstance instance = instances.get(instanceId);
        if (instance != null && !instance.isProvisional()) {
            return instance;
        }
        return null;
    }

    /// {@inheritDoc}
    @Override
    public int getInstanceCount() {
        int count = 0;
        for (DefaultGameInstance instance : instances.values()) {
            if (!instance.isProvisional()) {
                count++;
            }
        }
        return count;
    }

    /// {@inheritDoc}
    @Override
    public Collection<DefaultGameInstance> getInstances() {
        return instances.values().stream()
                .filter(instance -> !instance.isProvisional())
                .toList();
    }

    /// {@inheritDoc}
    @Override
    public Collection<GameInstanceManifest> getInstanceManifests() {
        return instances.values().stream()
                .filter(instance -> !instance.isProvisional())
                .map(instance -> instance.manifest)
                .toList();
    }

    /// Returns a view of all instances in this status, including provisional placeholders.
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

    /// Adds or replaces an instance in this unsealed status.
    ///
    /// @param instance the instance bound to this status
    public void put(DefaultGameInstance instance) {
        checkMutable();
        instances.put(instance.getId(), instance);
    }

    /// Adds or replaces all instances from the given map.
    ///
    /// @param map instances keyed by id
    public void putAll(Map<GameInstanceID, DefaultGameInstance> map) {
        checkMutable();
        instances.putAll(map);
    }

    /// Removes the instance with the given id.
    ///
    /// @param id the instance id
    public void remove(GameInstanceID id) {
        checkMutable();
        instances.remove(id);
    }

    /// Removes all instances from this unsealed status.
    public void clear() {
        checkMutable();
        instances.clear();
    }

    /// Creates an unsealed copy of this status with instances rebound to the copy.
    ///
    /// @return a mutable status ready for further edits before publish
    @Override
    public DefaultGameRepositoryStatus clone() {
        DefaultGameRepositoryStatus newStatus = newEmpty();
        for (DefaultGameInstance instance : instances.values()) {
            newStatus.put(instance.withNewStatus(newStatus));
        }
        return newStatus;
    }

    /// Resolves official-layout inheritance and patches into launch and standalone views.
    ///
    /// @param manifest the manifest to resolve
    /// @return the resolved manifest views
    /// @throws NoSuchGameInstanceException if an inherited parent is missing from this status
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest) throws NoSuchGameInstanceException {
        return resolve(manifest, new HashSet<>());
    }

    /// Resolves official-layout inheritance and patches into launch and standalone views.
    ///
    /// @param manifest      the manifest to resolve
    /// @param resolvedSoFar instance ids already visited in the inheritance chain
    /// @return the resolved manifest views
    /// @throws NoSuchGameInstanceException if an inherited parent is missing from this status
    public GameInstanceManifest.Resolved resolve(GameInstanceManifest manifest,
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
                LOG.warning("Found circular dependency versions: " + resolvedSoFar);
                launchManifest = (manifest.jar() == null ? manifest.withJar(manifest.id()) : manifest)
                        .withInheritsFrom(null);
            } else {
                DefaultGameInstance parentInstance = instances.get(manifest.inheritsFrom());
                if (parentInstance == null) {
                    throw new NoSuchGameInstanceException(manifest.inheritsFrom());
                }

                // It is supposed to auto-install a version in getVersion.
                GameInstanceManifest.Resolved parentResolved = resolve(parentInstance.getManifest(), resolvedSoFar);
                launchManifest = manifest.merge(parentResolved.launchManifest());
                standaloneManifest = addPatches(
                        addPatches(parentResolved.standaloneManifest(), Collections.singleton(manifest.toPatch())),
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

    private static GameInstanceManifest addPatches(GameInstanceManifest manifest, @Nullable Collection<GameInstancePatch> additional) {
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
