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

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.DefaultGameRepository;
import org.jackhuang.hmcl.game.DefaultGameRepositoryLayout;
import org.jackhuang.hmcl.game.DefaultGameRepositorySnapshot;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies capability aliases, version checks, reverse dependencies, and reachable JIJ selection.
@NotNullByDefault
public final class ModRelationIndexTest {
    /// Resolves provided aliases with their own versions and reports version mismatches.
    @Test
    public void testProvidedAliasVersion(@TempDir Path tempDirectory) {
        ModManager manager = manager(tempDirectory);
        LocalModFile provider = mod(manager, "implementation", "2.1.0", List.of(), Map.of("api", "2.1.0"));
        ModRelationIndex index = new ModRelationIndex(List.of(provider), "1.20.1");

        assertEquals(
                ModRelationIndex.Status.SATISFIED,
                index.resolve(new ModDependency("api", ">=2 <3", false, ModLoaderType.FABRIC)).status());
        assertEquals(
                ModRelationIndex.Status.VERSION_MISMATCH,
                index.resolve(new ModDependency("api", ">=3", false, ModLoaderType.FABRIC)).status());
    }

    /// Only cascades when the operation removes the final satisfying provider.
    @Test
    public void testLastProviderAndTransitiveCascade(@TempDir Path tempDirectory) {
        ModManager manager = manager(tempDirectory);
        LocalModFile first = mod(manager, "library", "2.0", List.of(), Map.of());
        LocalModFile second = new LocalModFile(
                manager,
                manager.getLocalMod("library", ModLoaderType.FABRIC),
                manager.getDirectory().resolve("sub/library.jar"),
                "library",
                new LocalAddonFile.Description(""),
                "", "2.1", "", "", "", List.of(), List.of(), Map.of());
        LocalModFile middle = mod(manager, "middle", "1.0", List.of(
                new ModDependency("library", ">=2", false, ModLoaderType.FABRIC)), Map.of());
        LocalModFile leaf = mod(manager, "leaf", "1.0", List.of(
                new ModDependency("middle", "*", false, ModLoaderType.FABRIC)), Map.of());
        List<LocalModFile> mods = List.of(first, second, middle, leaf);
        ModRelationIndex index = new ModRelationIndex(mods, "1.20.1");

        assertEquals(2, manager.getLocalMod("library", ModLoaderType.FABRIC).getFiles().size());
        assertTrue(index.findActiveDependents(List.of(first), mods).isEmpty());
        assertEquals(List.of(middle, leaf), index.findActiveDependents(List.of(first, second), mods));
    }

    /// Unselected wrapper branches do not contribute dependency constraints to reachable candidates.
    @Test
    public void testSelectedReachableNestedConstraints(@TempDir Path tempDirectory) {
        ModManager manager = manager(tempDirectory);
        LocalModFile wrapper = mod(manager, "wrapper", "1.0", List.of(
                new ModDependency("foo", "<2", false, ModLoaderType.FABRIC)), Map.of());
        NestedJarInspector.NestedJar foo1 = node("foo", "1.5", List.of(
                new ModDependency("lib", "<2", false, ModLoaderType.FABRIC)));
        NestedJarInspector.NestedJar foo2 = node("foo", "2.5", List.of(
                new ModDependency("lib", ">=2", false, ModLoaderType.FABRIC)));
        NestedJarInspector.NestedJar lib1 = node("lib", "1.8", List.of());
        NestedJarInspector.NestedJar lib2 = node("lib", "2.8", List.of());
        wrapper.setBundledTree(List.of(foo1, foo2, lib1, lib2));

        ModRelationIndex index = new ModRelationIndex(List.of(wrapper), "1.20.1");
        assertEquals("1.5", index.getProviders("foo").get(0).version());
        assertEquals("1.8", index.getProviders("lib").get(0).version());
    }

    /// Excludes providers parsed for a loader that the current instance cannot load.
    @Test
    public void testUnsupportedLoaderDoesNotProvide(@TempDir Path tempDirectory) {
        ModManager manager = manager(tempDirectory);
        LocalModFile forgeProvider = new LocalModFile(
                manager,
                manager.getLocalMod("library", ModLoaderType.FORGE),
                tempDirectory.resolve("forge-library.jar"),
                "forge-library",
                new LocalAddonFile.Description(""),
                "", "2.0", "", "", "", List.of(), List.of(), Map.of());
        ModRelationIndex index = new ModRelationIndex(
                List.of(forgeProvider), "1.20.1", Set.of(ModLoaderType.FABRIC), true);

        assertEquals(
                ModRelationIndex.Status.MISSING,
                index.resolve(new ModDependency("library", "*", false, ModLoaderType.FABRIC)).status());
    }

    /// Excludes a provider whose required Minecraft constraint rejects the current instance.
    @Test
    public void testMinecraftConstraintDoesNotProvide(@TempDir Path tempDirectory) {
        ModManager manager = manager(tempDirectory);
        LocalModFile provider = new LocalModFile(
                manager,
                manager.getLocalMod("library", ModLoaderType.FABRIC),
                tempDirectory.resolve("library.jar"),
                "library",
                new LocalAddonFile.Description(""),
                "", "2.0", "<1.20", "", "", List.of(), List.of(), Map.of(), List.of(), true);
        ModRelationIndex index = new ModRelationIndex(
                List.of(provider), "1.20.1", Set.of(ModLoaderType.FABRIC), true);

        assertEquals(
                ModRelationIndex.Status.MISSING,
                index.resolve(new ModDependency("library", "*", false, ModLoaderType.FABRIC)).status());
    }

    /// Creates a Fabric mod fixture attached to one stable test manager.
    private static LocalModFile mod(
            ModManager manager,
            String id,
            String version,
            List<ModDependency> dependencies,
            Map<String, String> providedVersions) {
        return new LocalModFile(
                manager,
                manager.getLocalMod(id, ModLoaderType.FABRIC),
                manager.getDirectory().resolve(id + ".jar"),
                id,
                new LocalAddonFile.Description(""),
                "", version, "", "", "", List.of(), dependencies, providedVersions);
    }

    /// Creates a nested Fabric node fixture.
    private static NestedJarInspector.NestedJar node(
            String id,
            String version,
            List<ModDependency> dependencies) {
        return new NestedJarInspector.NestedJar(
                id + "-" + version + ".jar",
                id + ".jar",
                id,
                id,
                version,
                ModLoaderType.FABRIC,
                null,
                false,
                dependencies,
                Map.of(),
                List.of(),
                null,
                null,
                null,
                List.of());
    }

    /// Creates a manager whose instance uses an isolated temporary repository.
    private static ModManager manager(Path root) {
        TestRepository repository = new TestRepository(root);
        return new ModManager(repository.createTestInstance());
    }

    /// Minimal repository used to construct an isolated snapshot member.
    private static final class TestRepository extends DefaultGameRepository {
        /// Creates the repository.
        private TestRepository(Path baseDirectory) {
            super(baseDirectory);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameRepositoryLayout createLayout(Path baseDirectory) {
            return new DefaultGameRepositoryLayout(baseDirectory);
        }

        /// {@inheritDoc}
        @Override
        protected TestGameInstance createInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            return new TestGameInstance(snapshot, id, manifest, manifestFile);
        }

        /// Creates one unpublished test instance bound to this repository.
        private TestGameInstance createTestInstance() {
            GameInstanceID id = new GameInstanceID("test");
            return createInstance(createSnapshot(getLayout()), id, new GameInstanceManifest(id), null);
        }
    }

    /// Minimal concrete instance used by relation-index fixtures.
    private static final class TestGameInstance extends DefaultGameInstance {
        /// Creates the instance.
        private TestGameInstance(
                DefaultGameRepositorySnapshot snapshot,
                GameInstanceID id,
                GameInstanceManifest manifest,
                @Nullable Path manifestFile) {
            super(snapshot, id, manifest, manifestFile);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameInstance withNewSnapshot(DefaultGameRepositorySnapshot newSnapshot) {
            return new TestGameInstance(newSnapshot, id, manifest, manifestFile);
        }

        /// {@inheritDoc}
        @Override
        protected DefaultGameInstance withManifest(
                DefaultGameRepositorySnapshot newSnapshot,
                GameInstanceManifest manifest) {
            return new TestGameInstance(newSnapshot, id, manifest, manifestFile);
        }
    }
}
