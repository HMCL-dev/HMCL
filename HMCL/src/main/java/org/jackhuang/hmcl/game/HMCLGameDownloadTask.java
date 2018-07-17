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

import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.FileDownloadTask.IntegrityCheck;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.NetworkUtils;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

/**
 * @author huangyuhui
 */
public class HMCLGameDownloadTask extends Task {
    private final Profile profile;
    private final String gameVersion;
    private final Version version;
    private final List<Task> dependencies = new LinkedList<>();

    public HMCLGameDownloadTask(Profile profile, String gameVersion, Version version) {
        this.profile = profile;
        this.gameVersion = gameVersion;
        this.version = version;

        setSignificance(TaskSignificance.MINOR);
    }

    @Override
    public List<Task> getDependencies() {
        return dependencies;
    }

    @Override
    public void execute() {
        File jar = profile.getRepository().getVersionJar(version);

        File cache = new File(ConfigHolder.CONFIG.commonDirectory.get(), "jars/" + gameVersion + ".jar");
        if (cache.exists())
            try {
                FileUtils.copyFile(cache, jar);
                return;
            } catch (IOException e) {
                Logging.LOG.log(Level.SEVERE, "Unable to copy cached Minecraft jar from " + cache + " to " + jar, e);
            }

        dependencies.add(new FileDownloadTask(
                NetworkUtils.toURL(profile.getDependency().getDownloadProvider().injectURL(version.getDownloadInfo().getUrl())),
                cache,
                profile.getDependency().getProxy(),
                new IntegrityCheck("SHA-1", version.getDownloadInfo().getSha1())
        ).then(Task.of(v -> FileUtils.copyFile(cache, jar))));
    }

}
