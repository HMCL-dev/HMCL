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

import java.nio.file.Path;

@NotNullByDefault
public class DefaultGameInstance implements GameInstance {

    private final DefaultGameRepository repository;
    private final DefaultGameRepositoryLayout layout;
    private final GameInstanceID id;
    private final GameInstanceManifest manifest;
    private GameInstanceManifest.@Nullable Resolved resolvedManifest;

    protected DefaultGameInstance(
            DefaultGameRepository.Status status,
            DefaultGameRepository repository, DefaultGameRepositoryLayout layout,
            GameInstanceID id, GameInstanceManifest manifest) {
        this.repository = repository;
        this.layout = layout;
        this.id = id;
        this.manifest = manifest;
    }

    @Override
    public GameRepository getRepository() {
        return repository;
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
            resolvedManifest = repository.resolve(manifest); // TODO
        }

        return resolvedManifest;
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
