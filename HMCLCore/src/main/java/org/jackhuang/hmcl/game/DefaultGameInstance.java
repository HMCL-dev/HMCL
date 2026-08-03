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

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashSet;

@NotNullByDefault
public class DefaultGameInstance implements GameInstance {

    protected final DefaultGameRepository.Status status;
    protected final DefaultGameRepository repository;
    protected final DefaultGameRepositoryLayout layout;
    protected final GameInstanceID id;
    protected final GameInstanceManifest manifest;
    protected GameInstanceManifest.@Nullable Resolved resolvedManifest;
    protected @Nullable GameVersionNumber version;

    protected DefaultGameInstance(
            DefaultGameRepository.Status status,
            GameInstanceID id,
            GameInstanceManifest manifest) {
        this.status = status;
        this.repository = status.repository;
        this.layout = status.layout;
        this.id = id;
        this.manifest = manifest;
    }

    protected DefaultGameInstance withNewStatus(DefaultGameRepository.Status newStatus) {
        return new DefaultGameInstance(newStatus, id, manifest);
    }

    @Override
    public DefaultGameRepository getRepository() {
        return repository;
    }

    @Override
    public DefaultGameRepositoryLayout getLayout() {
        return layout;
    }

    @Override
    public GameInstanceID getId() {
        return id;
    }

    @Override
    public GameInstanceManifest getManifest() {
        return manifest;
    }

    @Override
    public GameInstanceManifest.Resolved getResolvedManifest() {
        if (resolvedManifest == null) {
            resolvedManifest = status.resolve(manifest, new HashSet<>());
        }
        return resolvedManifest;
    }

    @Override
    public GameVersionNumber getVersion() {
        if (version == null) {
            version = GameVersionNumber.asGameVersion(repository.getGameVersion(getId())); // TODO
        }
        return version;
    }

    @Override
    public Path getInstanceRoot() {
        return layout.getInstanceRoot(id);
    }

    @Override
    public Path getInstanceJarFile() {
        return layout.getInstanceJarFile(id);
    }

    @Override
    public Path getRunDirectory() {
        return layout.getBaseDirectory();
    }
}
