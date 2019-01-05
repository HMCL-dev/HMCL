/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author huangyuhui
 */
public final class GameVersionList extends VersionList<GameRemoteVersion> {

    public static final GameVersionList INSTANCE = new GameVersionList();

    private GameVersionList() {
    }

    @Override
    public boolean hasType() {
        return true;
    }

    @Override
    protected Collection<GameRemoteVersion> getVersionsImpl(String gameVersion) {
        lock.readLock().lock();
        try {
            return StringUtils.isBlank(gameVersion) ? versions.values() : versions.get(gameVersion);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        GetTask task = new GetTask(NetworkUtils.toURL(downloadProvider.getVersionListURL()));
        return new Task() {
            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() {
                lock.writeLock().lock();

                try {
                    versions.clear();

                    GameRemoteVersions root = JsonUtils.GSON.fromJson(task.getResult(), GameRemoteVersions.class);
                    for (GameRemoteVersionInfo remoteVersion : root.getVersions()) {
                        versions.put(remoteVersion.getGameVersion(), new GameRemoteVersion(
                                remoteVersion.getGameVersion(),
                                remoteVersion.getGameVersion(),
                                remoteVersion.getUrl(),
                                remoteVersion.getType(), remoteVersion.getReleaseTime())
                        );
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        };
    }
}
