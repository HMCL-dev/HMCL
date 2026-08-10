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

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default [GameRepositoryDraft] that keeps the published snapshot immutable and stages stored
/// manifests until [#commit()].
///
/// The draft holds [#base] (the repository snapshot at open time) and [#staged] manifests.
/// [#put(GameInstanceManifest)] only updates that map and instance JSON. [#commit()] clones
/// [#base] once, applies staged manifests, and publishes the result. No [GameInstance] is produced
/// by the draft itself.
@NotNullByDefault
public final class DefaultGameRepositoryDraft implements GameRepositoryDraft {

    /// Repository whose published index will be replaced on [#commit()].
    private final DefaultGameRepository repository;

    /// Immutable published snapshot captured when this draft was opened.
    private final DefaultGameRepositorySnapshot base;

    /// Staged stored manifests keyed by instance id; applied only on [#commit()].
    private final Map<GameInstanceID, GameInstanceManifest> staged = new HashMap<>();

    /// Instance ids that were not present in [#base] when first staged.
    private final Set<GameInstanceID> createdIds = new HashSet<>();

    /// Base stored manifests for ids that existed in [#base] and were later staged.
    ///
    /// Used by [#abort()] to restore on-disk JSON.
    private final Map<GameInstanceID, GameInstanceManifest> originalManifests = new HashMap<>();

    /// Snapshot published by [#commit()], or `null` before a successful commit.
    private @Nullable DefaultGameRepositorySnapshot committedSnapshot;

    /// Whether [#commit()] has completed successfully.
    private boolean committed;

    /// Whether this draft no longer accepts mutations.
    private boolean closed;

    /// Creates a draft over `repository`'s current published snapshot as an immutable base.
    ///
    /// @param repository the repository that owns this draft
    DefaultGameRepositoryDraft(DefaultGameRepository repository) {
        this.repository = repository;
        this.base = repository.getSnapshot();
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    /// {@inheritDoc}
    ///
    /// Returns [#base] while open, or the snapshot published by [#commit()] after success.
    ///
    /// @throws IllegalStateException if the draft was aborted or closed without commit
    @Override
    public DefaultGameRepositorySnapshot getSnapshot() {
        if (committed) {
            return committedSnapshot;
        }
        if (closed) {
            throw new IllegalStateException("Draft is closed");
        }
        return base;
    }

    /// {@inheritDoc}
    @Override
    public boolean isOpen() {
        return !closed;
    }

    /// {@inheritDoc}
    @Override
    public boolean isCommitted() {
        return committed;
    }

    /// {@inheritDoc}
    @Override
    public boolean hasInstance(GameInstanceID instanceId) {
        checkOpen();
        return staged.containsKey(instanceId) || base.hasInstance(instanceId);
    }

    /// {@inheritDoc}
    ///
    /// Writes `manifest` to disk and records it in [#staged]. Does not modify [#base] and does not
    /// create a [GameInstance].
    ///
    /// @throws IllegalStateException if the draft is closed
    @Override
    public void put(GameInstanceManifest manifest) throws IOException {
        checkOpen();

        GameInstanceID id = manifest.id();
        DefaultGameInstance existingInBase = base.get(id);
        if (existingInBase != null) {
            originalManifests.putIfAbsent(id, existingInBase.getManifest());
        } else if (!staged.containsKey(id)) {
            createdIds.add(id);
        }

        Path json = repository.getInstanceJson(id).toAbsolutePath();
        Files.createDirectories(json.getParent());
        JsonUtils.writeToJsonFile(json, manifest);

        staged.put(id, manifest);
    }

    /// {@inheritDoc}
    ///
    /// Clones [#base], applies all staged manifests onto the clone, seals it, and publishes it.
    ///
    /// @throws IllegalStateException if the draft is closed or already committed
    @Override
    public void commit() {
        checkOpen();
        if (committed) {
            throw new IllegalStateException("Draft already committed");
        }

        DefaultGameRepositorySnapshot next = base.clone();
        for (Map.Entry<GameInstanceID, GameInstanceManifest> entry : staged.entrySet()) {
            GameInstanceID id = entry.getKey();
            GameInstanceManifest manifest = entry.getValue();
            DefaultGameInstance existing = next.get(id);
            if (existing != null) {
                next.put(existing.withManifest(next, manifest));
            } else {
                next.put(repository.createInstance(next, id, manifest));
            }
        }

        repository.publishSnapshot(next);
        committedSnapshot = next;
        committed = true;
        closed = true;
    }

    /// {@inheritDoc}
    ///
    /// Idempotent when already aborted. Does not publish a snapshot.
    ///
    /// @throws IllegalStateException if the draft was already committed
    @Override
    public void abort() {
        if (committed) {
            throw new IllegalStateException("Draft already committed");
        }
        if (closed) {
            return;
        }
        closed = true;

        for (GameInstanceID id : createdIds) {
            try {
                deleteInstanceDirectory(id);
            } catch (Exception e) {
                LOG.warning("Failed to remove draft-created instance " + id, e);
            }
        }

        for (Map.Entry<GameInstanceID, GameInstanceManifest> entry : originalManifests.entrySet()) {
            if (createdIds.contains(entry.getKey())) {
                continue;
            }
            try {
                Path json = repository.getInstanceJson(entry.getKey()).toAbsolutePath();
                Files.createDirectories(json.getParent());
                JsonUtils.writeToJsonFile(json, entry.getValue());
            } catch (IOException e) {
                LOG.warning("Failed to restore manifest for " + entry.getKey(), e);
            }
        }
    }

    /// {@inheritDoc}
    ///
    /// Calls [#abort()] when the draft is still open and not committed.
    @Override
    public void close() {
        if (!closed && !committed) {
            abort();
        }
    }

    /// Deletes a draft-created instance directory without modifying the published snapshot.
    ///
    /// @param id the instance id whose root directory will be removed
    /// @throws IOException if deletion fails
    private void deleteInstanceDirectory(GameInstanceID id) throws IOException {
        Path root = repository.getLayout().getInstanceRoot(id);
        if (Files.notExists(root)) {
            return;
        }
        FileUtils.deleteDirectory(root);
    }

    /// Ensures the draft still accepts mutations.
    ///
    /// @throws IllegalStateException if the draft is closed
    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Draft is closed");
        }
    }
}
