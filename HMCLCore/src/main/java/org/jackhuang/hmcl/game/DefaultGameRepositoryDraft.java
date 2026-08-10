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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Default [GameRepositoryDraft] that holds a COW clone of the published snapshot as its working
/// index.
///
/// [#put(GameInstanceManifest)] writes instance JSON immediately and updates [#getSnapshot()].
/// [#commit()] seals and publishes that snapshot. [#abort()] restores JSON for instances modified
/// in this draft and deletes directories created only here; it does not revert library or asset
/// downloads outside instance roots.
@NotNullByDefault
public final class DefaultGameRepositoryDraft implements GameRepositoryDraft {

    /// Repository whose published index will be replaced on [#commit()].
    private final DefaultGameRepository repository;

    /// Working snapshot for this draft; published on [#commit()].
    private final DefaultGameRepositorySnapshot snapshot;

    /// Instance ids that did not exist in the working snapshot when first staged by [#put].
    private final Set<GameInstanceID> createdIds = new HashSet<>();

    /// First observed stored manifest for each id that already existed when staged by [#put].
    ///
    /// Used by [#abort()] to restore on-disk JSON for edited instances.
    private final Map<GameInstanceID, GameInstanceManifest> originalManifests = new HashMap<>();

    /// Whether [#commit()] has completed successfully.
    private boolean committed;

    /// Whether this draft no longer accepts mutations.
    private boolean closed;

    /// Creates a draft whose working snapshot is a clone of `repository`'s published snapshot.
    ///
    /// @param repository the repository that owns this draft
    DefaultGameRepositoryDraft(DefaultGameRepository repository) {
        this.repository = repository;
        this.snapshot = repository.getSnapshot().clone();
    }

    /// {@inheritDoc}
    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    /// {@inheritDoc}
    ///
    /// @throws IllegalStateException if the draft was aborted or closed without commit
    @Override
    public DefaultGameRepositorySnapshot getSnapshot() {
        if (closed && !committed) {
            throw new IllegalStateException("Draft is closed");
        }
        return snapshot;
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
    ///
    /// Writes `manifest` to the instance JSON path, records abort metadata, and replaces or creates
    /// the instance entry in [#getSnapshot()].
    ///
    /// @throws IllegalStateException if the draft is closed
    @Override
    public DefaultGameInstance put(GameInstanceManifest manifest) throws IOException {
        checkOpen();

        GameInstanceID id = manifest.id();
        DefaultGameInstance existing = snapshot.get(id);
        if (existing != null) {
            originalManifests.putIfAbsent(id, existing.getManifest());
        } else {
            createdIds.add(id);
        }

        Path json = repository.getInstanceJson(id).toAbsolutePath();
        Files.createDirectories(json.getParent());
        JsonUtils.writeToJsonFile(json, manifest);

        if (existing != null) {
            snapshot.put(existing.withManifest(snapshot, manifest));
        } else {
            snapshot.put(repository.createInstance(snapshot, id, manifest));
        }
        return snapshot.getRegistered(id);
    }

    /// {@inheritDoc}
    ///
    /// Seals [#getSnapshot()] and installs it as the repository's published index. After this method
    /// returns, the draft is closed and only [#isCommitted()] / [#getSnapshot()] remain meaningful.
    ///
    /// @throws IllegalStateException if the draft is closed or already committed
    @Override
    public void commit() {
        checkOpen();
        if (committed) {
            throw new IllegalStateException("Draft already committed");
        }
        repository.publishSnapshot(snapshot);
        committed = true;
        closed = true;
    }

    /// {@inheritDoc}
    ///
    /// Idempotent when already aborted. Does not publish the working snapshot.
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

    /// {@inheritDoc}
    ///
    /// Calls [#abort()] when the draft is still open and not committed.
    @Override
    public void close() {
        if (!closed && !committed) {
            abort();
        }
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
