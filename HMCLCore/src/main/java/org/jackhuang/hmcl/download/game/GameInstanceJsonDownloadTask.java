// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.game;

import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.task.TaskSignificance;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 *
 * @author huangyuhui
 */
public final class GameInstanceJsonDownloadTask extends Task<String> {
    private final String gameVersion;
    private final DefaultDependencyManager dependencyManager;

    /// Creates a lookup for the requested version's remote manifest.
    public GameInstanceJsonDownloadTask(String gameVersion, DefaultDependencyManager dependencyManager) {
        this.gameVersion = gameVersion;
        this.dependencyManager = Objects.requireNonNull(dependencyManager);
        setSignificance(TaskSignificance.MODERATE);
    }

    /// Returns the remote manifest JSON for the requested game version.
    @Override
    public String execute(TaskExecutionContext context) throws Exception {
        DownloadProvider downloadProvider = dependencyManager.getDownloadProvider();
        ComponentRemoteVersionList<?> versions = context.run(downloadProvider.getVersionsAsync(GameComponentType.GAME, null, false));
        @Nullable ComponentRemoteVersion remoteVersion = versions.getRemoteVersion(gameVersion);
        if (remoteVersion == null)
            throw new IOException(new IOException("Cannot find specific version " + gameVersion + " in remote repository"));
        return context.run(new GetTask(downloadProvider.getDownloadCandidates(remoteVersion)));
    }
}
