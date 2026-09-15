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

/// Publishes isolated [DefaultGameInstance]s for tests that live outside this package.
///
/// The fixture sits in [org.jackhuang.hmcl.game] because publishing an instance requires
/// [DefaultGameRepositorySnapshot#put], which is package-private. Instances it hands out run
/// entirely against a temporary directory and never touch the network or the JavaFX toolkit.
@NotNullByDefault
public final class TestGameInstanceFixture extends DefaultGameRepository {

    /// Creates a repository rooted at the given directory.
    ///
    /// @param baseDirectory the repository base directory; also the instance run directory
    public TestGameInstanceFixture(Path baseDirectory) {
        super(baseDirectory);
    }

    @Override
    protected DefaultGameRepositoryLayout createLayout(Path baseDirectory) {
        return new DefaultGameRepositoryLayout(baseDirectory);
    }

    @Override
    protected DefaultGameInstance createInstance(
            DefaultGameRepositorySnapshot snapshot,
            GameInstanceID id,
            GameInstanceManifest manifest,
            @Nullable Path manifestFile) {
        return new FixtureGameInstance(snapshot, id, manifest, manifestFile);
    }

    /// Publishes a snapshot holding one instance with the given id and no game components.
    ///
    /// The instance has no mod loader, so a mod manager bound to it treats every reader as
    /// unsupported and still parses any recognized mod file it finds.
    ///
    /// @param id the instance id
    /// @return the published instance
    public DefaultGameInstance publishInstance(String id) {
        GameInstanceID instanceId = new GameInstanceID(id);
        DefaultGameRepositorySnapshot snapshot = createSnapshot(getLayout());
        FixtureGameInstance instance = new FixtureGameInstance(
                snapshot,
                instanceId,
                new GameInstanceManifest(instanceId),
                null);
        snapshot.put(instance);
        publishSnapshot(snapshot);
        return instance;
    }

    /// Minimal concrete game instance used by the fixture.
    @NotNullByDefault
    private static final class FixtureGameInstance extends DefaultGameInstance {

        /// Creates an instance bound to the given snapshot.
        ///
        /// @param snapshot     the owning snapshot
        /// @param id           the instance id
        /// @param manifest     the stored instance manifest
        /// @param manifestFile the actual manifest path, or `null` for the layout default
        private FixtureGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            super(snapshot, id, manifest, manifestFile);
        }

        @Override
        protected FixtureGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
            return new FixtureGameInstance(newSnapshot, id, manifest, null);
        }

        @Override
        protected FixtureGameInstance withManifest(
                DefaultGameRepositorySnapshot newSnapshot,
                GameInstanceManifest manifest) {
            return new FixtureGameInstance(newSnapshot, id, manifest, null);
        }
    }
}
