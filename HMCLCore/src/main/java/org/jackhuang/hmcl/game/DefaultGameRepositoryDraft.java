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

/// Default [GameRepositoryDraft] backed by a COW clone of the published snapshot.
@NotNullByDefault
public final class DefaultGameRepositoryDraft implements GameRepositoryDraft {

    private final DefaultGameRepository repository;
    private final DefaultGameRepositorySnapshot working;
    private final Set<GameInstanceID> createdIds = new HashSet<>();
    private final Map<GameInstanceID, GameInstanceManifest> originalManifests = new HashMap<>();
    private boolean committed;
    private boolean closed;

    DefaultGameRepositoryDraft(DefaultGameRepository repository) {
        this.repository = repository;
        this.working = repository.getSnapshot().clone();
    }

    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public boolean hasInstance(GameInstanceID instanceId) {
        checkOpen();
        return working.hasInstance(instanceId);
    }

    @Override
    public DefaultGameInstance getInstance(GameInstanceID instanceId) throws NoSuchGameInstanceException {
        checkOpen();
        return working.getRegistered(instanceId);
    }

    @Override
    public DefaultGameInstance put(GameInstanceManifest manifest) throws IOException {
        checkOpen();

        GameInstanceID id = manifest.id();
        DefaultGameInstance existing = working.get(id);
        if (existing != null) {
            originalManifests.putIfAbsent(id, existing.getManifest());
        } else {
            createdIds.add(id);
        }

        Path json = repository.getInstanceJson(id).toAbsolutePath();
        Files.createDirectories(json.getParent());
        JsonUtils.writeToJsonFile(json, manifest);

        if (existing != null) {
            working.put(existing.withManifest(working, manifest));
        } else {
            working.put(repository.createInstance(working, id, manifest));
        }
        return working.getRegistered(id);
    }

    @Override
    public void commit() {
        checkOpen();
        if (committed) {
            throw new IllegalStateException("Draft already committed");
        }
        repository.publishSnapshot(working);
        committed = true;
        closed = true;
    }

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

    /// Deletes a draft-created instance directory without touching the published snapshot.
    private void deleteInstanceDirectory(GameInstanceID id) throws IOException {
        Path root = repository.getLayout().getInstanceRoot(id);
        if (Files.notExists(root)) {
            return;
        }
        FileUtils.deleteDirectory(root);
    }

    @Override
    public void close() {
        if (!closed && !committed) {
            abort();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Draft is closed");
        }
    }
}
