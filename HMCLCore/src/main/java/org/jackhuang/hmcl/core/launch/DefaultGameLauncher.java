/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hmcl.core.launch;

import org.jackhuang.hmcl.api.game.LaunchOptions;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import org.jackhuang.hmcl.core.download.DownloadLibraryJob;
import org.jackhuang.hmcl.api.auth.LoginInfo;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.ui.MessageBox;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.CompressingUtils;
import org.jackhuang.hmcl.util.task.ParallelTask;
import org.jackhuang.hmcl.util.task.TaskWindow;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.game.DecompressLibraryJob;

public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(LaunchOptions options, IMinecraftService service, LoginInfo info, IAuthenticator lg) {
        super(options, service, info, lg);
    }

    @Override
    public boolean downloadLibraries(List<DownloadLibraryJob> jobs) {
        final TaskWindow.TaskWindowFactory dw = TaskWindow.factory();
        ParallelTask parallelTask = new ParallelTask();
        for (DownloadLibraryJob s : jobs)
            parallelTask.addTask(new LibraryDownloadTask(s, service.getDownloadType()));
        dw.append(parallelTask);
        boolean flag = true;
        if (jobs.size() > 0)
            flag = dw.execute();
        if (!flag && MessageBox.show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
            flag = true;
        return flag;
    }

    @Override
    public boolean decompressLibraries(DecompressLibraryJob job) {
        if (job == null)
            return false;
        for (int i = 0; i < job.decompressFiles.length; i++)
            try {
                CompressingUtils.unzip(job.decompressFiles[i], job.getDecompressTo(), job.extractRules[i]::allow, false);
            } catch (IOException ex) {
                HMCLog.err("Unable to decompress library: " + job.decompressFiles[i] + " to " + job.getDecompressTo(), ex);
            }
        return true;
    }

}
