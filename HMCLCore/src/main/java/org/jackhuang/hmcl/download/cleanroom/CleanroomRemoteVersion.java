// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.cleanroom;

import com.google.gson.annotations.SerializedName;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.TreeSet;

@NotNullByDefault
public final class CleanroomRemoteVersion extends ComponentRemoteVersion {
    private static final GameVersionNumber GAME_VERSION_1_12_2 = GameVersionNumber.asGameVersion("1.12.2");

    public static final WebURL LOADER_LIST_URL = WebURL.parse("https://hmcl.glavo.site/metadata/cleanroom/index.json");

    public static Task<ComponentRemoteVersionList<CleanroomRemoteVersion>> fetchAsync(
            DownloadProvider downloadProvider, GameVersionNumber gameVersion) {
        if (!gameVersion.equals(GAME_VERSION_1_12_2)) {
            return Task.completed(ComponentRemoteVersionList.of(GameComponentType.CLEANROOM));
        }

        @JsonSerializable
        record ReleaseResult(String name, @SerializedName("created_at") String createdAt) {
        }

        return new GetTask(downloadProvider.getCleanroomVersionListCandidates()).thenApplyAsync(result -> {
            var results = JsonUtils.fromNonNullJson(result, JsonUtils.listTypeOf(ReleaseResult.class));

            var versions = new TreeSet<CleanroomRemoteVersion>();
            for (ReleaseResult version : results) {
                versions.add(new CleanroomRemoteVersion(
                        GAME_VERSION_1_12_2, version.name, Instant.parse(version.createdAt),
                        List.of("https://hmcl.glavo.site/metadata/cleanroom/files/cleanroom-%s-installer.jar".formatted(version.name))
                ));
            }
            return ComponentRemoteVersionList.of(GameComponentType.CLEANROOM, versions);
        });
    }

    public CleanroomRemoteVersion(GameVersionNumber gameVersion, String selfVersion, Instant releaseDate, List<String> url) {
        super(GameComponentType.CLEANROOM, gameVersion, selfVersion, releaseDate, Type.UNCATEGORIZED, url);
    }

    @Override
    public Task<GameInstancePatch> getInstallTask(DefaultDependencyManager dependencyManager, GameInstanceManifest baseManifest, Path modsDirectory) {
        return new CleanroomInstallTask(dependencyManager, baseManifest, this);
    }
}
