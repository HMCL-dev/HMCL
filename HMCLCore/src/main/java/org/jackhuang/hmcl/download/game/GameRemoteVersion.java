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
package org.jackhuang.hmcl.download.game;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.game.ReleaseType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Immutable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author huangyuhui
@Immutable
@NotNullByDefault
public final class GameRemoteVersion extends ComponentRemoteVersion {

    public static final WebURL VERSION_MANIFEST_URL = WebURL.parse("https://piston-meta.mojang.com/mc/game/version_manifest.json");

    public static Task<ComponentRemoteVersionList<GameRemoteVersion>> fetchAsync(DownloadProvider provider) {
        return new GetTask(provider.getGameVersionListCandidates())
                .thenApplyAsync(json -> {
                    GameRemoteVersions root = JsonUtils.fromNonNullJson(json, GameRemoteVersions.class);

                    GameRemoteVersions unlistedVersions = null;

                    //noinspection DataFlowIssue
                    try (Reader input = new InputStreamReader(
                            GameRemoteVersion.class.getResourceAsStream("/assets/game/unlisted-versions.json"))) {
                        unlistedVersions = JsonUtils.GSON.fromJson(input, GameRemoteVersions.class);
                    } catch (Throwable e) {
                        LOG.warning("Failed to load unlisted versions", e);
                    }

                    var versions = new TreeSet<GameRemoteVersion>();

                    if (unlistedVersions != null) {
                        for (GameRemoteVersionInfo unlistedVersion : unlistedVersions.versions()) {
                            versions.add(new GameRemoteVersion(
                                    GameVersionNumber.asGameVersion(unlistedVersion.gameVersion()),
                                    List.of(unlistedVersion.url()),
                                    unlistedVersion.type(), unlistedVersion.releaseTime()));
                        }
                    }

                    for (GameRemoteVersionInfo remoteVersion : root.versions()) {
                        versions.add(new GameRemoteVersion(
                                GameVersionNumber.asGameVersion(remoteVersion.gameVersion()),
                                List.of(remoteVersion.url()),
                                remoteVersion.type(), remoteVersion.releaseTime()));
                    }

                    return ComponentRemoteVersionList.of(GameComponentType.GAME, versions);
                });
    }

    private final ReleaseType type;

    public GameRemoteVersion(GameVersionNumber gameVersion, List<String> url, ReleaseType type, Instant releaseDate) {
        super(GameComponentType.GAME, gameVersion, gameVersion.toString(), releaseDate, getReleaseType(type), url);
        this.type = type;
    }

    public ReleaseType getType() {
        return type;
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new GameInstallTask(dependencyManager, baseManifest, this);
    }

    @Override
    public int compareTo(ComponentRemoteVersion o) {
        if (!(o instanceof GameRemoteVersion)) {
            return this.getComponentType().compareTo(o.getComponentType());
        }

        int dateCompare = o.getReleaseDate().compareTo(getReleaseDate());
        if (dateCompare != 0) {
            return dateCompare;
        }

        return o.getGameVersion().compareTo(getGameVersion());
    }

    private static Type getReleaseType(ReleaseType type) {
        if (type == null) return Type.UNCATEGORIZED;
        return switch (type) {
            case RELEASE -> Type.RELEASE;
            case SNAPSHOT -> Type.SNAPSHOT;
            case UNKNOWN -> Type.UNCATEGORIZED;
            case PENDING -> Type.PENDING;
            case UNOBFUSCATED -> Type.UNOBFUSCATED;
            default -> Type.OLD;
        };
    }
}
