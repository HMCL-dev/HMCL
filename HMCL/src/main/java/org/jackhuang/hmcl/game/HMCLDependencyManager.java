/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.ParallelTask;
import org.jackhuang.hmcl.task.Task;

/**
 * @author huangyuhui
 */
public class HMCLDependencyManager extends DefaultDependencyManager {
    private final Profile profile;

    public HMCLDependencyManager(Profile profile, DownloadProvider downloadProvider) {
        super(profile.getRepository(), downloadProvider);

        this.profile = profile;
    }

    @Override
    public GameBuilder gameBuilder() {
        return new HMCLGameBuilder(profile);
    }

    @Override
    public Task checkGameCompletionAsync(Version version) {
        return new ParallelTask(
                new HMCLGameAssetDownloadTask(this, version),
                new HMCLGameLibrariesTask(this, version)
        );
    }

    @Override
    public Task checkLibraryCompletionAsync(Version version) {
        return new HMCLGameLibrariesTask(this, version);
    }
}
