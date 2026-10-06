/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.ComponentRemoteVersionList;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class LiteLoaderRemoteVersion extends ComponentRemoteVersion {

    public static Task<ComponentRemoteVersionList<LiteLoaderRemoteVersion>> fetchAsync(GameVersionNumber gameVersion) {
        @JsonSerializable
        record LiteLoaderRemoteVersionRecord(
                String gameVersion,
                String version,
                boolean snapshot,
                String url,
                String tweakClass,
                List<Library> libraries
        ) {
        }

        return Task.supplyAsync(() -> {
            List<LiteLoaderRemoteVersionRecord> records;

            try (var input = LiteLoaderRemoteVersion.class.getResourceAsStream("/assets/liteloader/versions.json")) {
                if (input == null) {
                    LOG.warning("Failed to load LiteLoader versions.json from resources");
                    return ComponentRemoteVersionList.of(GameComponentType.LITELOADER);
                }

                records = JsonUtils.GSON.fromJson(
                        new String(input.readAllBytes(), StandardCharsets.UTF_8),
                        JsonUtils.listTypeOf(LiteLoaderRemoteVersionRecord.class));
            }

            var versions = new TreeSet<LiteLoaderRemoteVersion>();
            for (var record : records) {
                if (GameVersionNumber.asGameVersion(record.gameVersion).equals(gameVersion)) {
                    versions.add(new LiteLoaderRemoteVersion(
                            gameVersion,
                            record.version,
                            record.snapshot ? Type.SNAPSHOT : Type.RELEASE,
                            List.of(record.url),
                            record.tweakClass,
                            List.copyOf(record.libraries)
                    ));
                }
            }
            return ComponentRemoteVersionList.of(GameComponentType.LITELOADER, versions);
        });
    }

    private final String tweakClass;
    private final Collection<Library> libraries;

    /**
     * Constructor.
     *
     * @param gameVersion the Minecraft version that this remote version suits.
     * @param selfVersion the version string of the remote version.
     * @param urls        the installer or universal jar original URL.
     */
    LiteLoaderRemoteVersion(GameVersionNumber gameVersion, String selfVersion, Type type, List<String> urls, String tweakClass, Collection<Library> libraries) {
        super(GameComponentType.LITELOADER, gameVersion, selfVersion, null, type, urls);

        this.tweakClass = tweakClass;
        this.libraries = libraries;
    }

    public Collection<Library> getLibraries() {
        return libraries;
    }

    public String getTweakClass() {
        return tweakClass;
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new LiteLoaderInstallTask(dependencyManager, baseManifest, this);
    }
}
