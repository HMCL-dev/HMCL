/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.DownloadInfo;
import org.jackhuang.hmcl.game.DownloadType;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Glavo
 */
public final class GameServerDownloadTask extends Task<Void> {
    private final DownloadProvider downloadProvider;
    private final Path path;
    private final GameRemoteVersion gameVersion;
    private final Task<String> getJsonTask;

    private final List<Task<?>> dependencies = new ArrayList<>(1);

    public GameServerDownloadTask(DownloadProvider downloadProvider, Path path, GameRemoteVersion gameRemoteVersion) {
        this.downloadProvider = downloadProvider;
        this.path = path;
        this.gameVersion = gameRemoteVersion;
        this.getJsonTask = new GetTask(downloadProvider.injectURLsWithCandidates(gameRemoteVersion.getUrls()));
    }

    @Override
    public List<Task<?>> getDependents() {
        return List.of(getJsonTask);
    }

    @Override
    public void execute() throws Exception {
        String versionJson = getJsonTask.getResult();
        Version version = JsonUtils.GSON.fromJson(versionJson, Version.class);
        if (version == null)
            throw new IOException("version is null");

        DownloadInfo downloadInfo = version.getDownloads().get(DownloadType.SERVER);
        if (downloadInfo == null)
            throw new IOException("Missing server download info");

        dependencies.add(new FileDownloadTask(
                downloadProvider.injectURLWithCandidates(downloadInfo.getUrl()),
                path,
                FileDownloadTask.IntegrityCheck.of("SHA-1", downloadInfo.getSha1())));
    }

    @Override
    public List<Task<?>> getDependencies() {
        return dependencies;
    }
}
