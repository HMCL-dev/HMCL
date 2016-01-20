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
package org.jackhuang.hellominecraft.launcher.core.launch;

import java.io.IOException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.tasks.ParallelTask;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.utils.system.Compressor;
import org.jackhuang.hellominecraft.utils.MessageBox;

public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(LaunchOptions options, IMinecraftService service, LoginInfo info, IAuthenticator lg) {
        super(options, service, info, lg);
        register();
    }

    private void register() {
        downloadLibrariesEvent.register((sender, t) -> {
            final TaskWindow.TaskWindowFactory dw = TaskWindow.getInstance();
            ParallelTask parallelTask = new ParallelTask();
            for (DownloadLibraryJob s : t)
                parallelTask.addDependsTask(new LibraryDownloadTask(s));
            dw.addTask(parallelTask);
            boolean flag = true;
            if (t.size() > 0)
                flag = dw.start();
            if (!flag && MessageBox.Show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                flag = true;
            return flag;
        });
        decompressNativesEvent.register((sender, value) -> {
            if (value == null)
                return false;
            for (int i = 0; i < value.decompressFiles.length; i++)
                try {
                    Compressor.unzip(value.decompressFiles[i], value.decompressTo, value.extractRules[i]);
                } catch (IOException ex) {
                    HMCLog.err("Unable to decompress library file: " + value.decompressFiles[i] + " to " + value.decompressTo, ex);
                }
            return true;
        });
    }

}
