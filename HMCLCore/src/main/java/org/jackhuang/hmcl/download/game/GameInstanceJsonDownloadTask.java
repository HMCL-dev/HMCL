// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 *
 * @author huangyuhui
 */
public final class GameInstanceJsonDownloadTask extends Task<String> {
    private final String gameVersion;
    private final DefaultDependencyManager dependencyManager;
    private final Task<ComponentRemoteVersionList<?>> getGameVersionsTask;

    public GameInstanceJsonDownloadTask(String gameVersion, DefaultDependencyManager dependencyManager) {
        this.gameVersion = gameVersion;
        this.dependencyManager = dependencyManager;

        getGameVersionsTask = dependencyManager.getDownloadProvider()
                .getVersionsAsync(GameComponentType.GAME, null, false);

        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the remote manifest JSON for the requested game version.
    @Override
    public String execute(TaskExecutionContext context) throws Exception {
        @Nullable ComponentRemoteVersion remoteVersion = context.run(getGameVersionsTask).getRemoteVersion(gameVersion);
        if (remoteVersion == null)
            throw new IOException(new IOException("Cannot find specific version " + gameVersion + " in remote repository"));
        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        return context.run(new GetTask(downloadProvider.getDownloadCandidates(remoteVersion)));
    }
}
