/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.IOException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.utils.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.utils.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.tasks.ParallelTask;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.Compressor;
import org.jackhuang.hellominecraft.utils.MessageBox;

public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(Profile version, LoginInfo info, IAuthenticator lg) {
        super(version, info, lg);
        register();
    }

    public DefaultGameLauncher(Profile version, LoginInfo info, IAuthenticator lg, DownloadType downloadType) {
        super(version, info, lg, downloadType);
        register();
    }

    private void register() {
        downloadLibrariesEvent.register((sender, t) -> {
            final TaskWindow dw = TaskWindow.getInstance();
            ParallelTask parallelTask = new ParallelTask();
            for (DownloadLibraryJob o : t) {
                final DownloadLibraryJob s = (DownloadLibraryJob) o;
                parallelTask.addDependsTask(new FileDownloadTask(s.url, s.path).setTag(s.name));
            }
            dw.addTask(parallelTask);
            boolean flag = true;
            if (t.size() > 0) flag = dw.start();
            if (!flag && MessageBox.Show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                flag = true;
            return flag;
        });
        decompressNativesEvent.register((sender, value) -> {
            //boolean flag = true;
            for (int i = 0; i < value.decompressFiles.length; i++)
                try {
                    Compressor.unzip(value.decompressFiles[i], value.decompressTo, value.extractRules[i]);
                } catch (IOException ex) {
                    HMCLog.err("Unable to decompress library file: " + value.decompressFiles[i] + " to " + value.decompressTo, ex);
                    //flag = false;
                }
            /*if(!flag)
            if(MessageBox.Show(C.i18n("launch.not_finished_decompressing_natives"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
            flag = true;*/
            return true;
        });
    }

}
