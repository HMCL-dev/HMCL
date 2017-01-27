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
import java.util.HashSet;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.util.tasks.ParallelTask;
import org.jackhuang.hellominecraft.util.tasks.TaskWindow;
import org.jackhuang.hellominecraft.util.system.CompressingUtils;
import org.jackhuang.hellominecraft.util.MessageBox;

public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(LaunchOptions options, IMinecraftService service, LoginInfo info, IAuthenticator lg) {
        super(options, service, info, lg);
        register();
    }

    private void register() {
        downloadLibrariesEvent.register((sender, t) -> {
            final TaskWindow.TaskWindowFactory dw = TaskWindow.factory();
            ParallelTask parallelTask = new ParallelTask();
            HashSet<String> names = new HashSet<>();
            for (DownloadLibraryJob s : t) {
                if (names.contains(s.lib.name))
                    continue;
                names.add(s.lib.name);
                parallelTask.addTask(new LibraryDownloadTask(s));
            }
            dw.append(parallelTask);
            boolean flag = true;
            if (t.size() > 0)
                flag = dw.execute();
            if (!flag && MessageBox.show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                flag = true;
            return flag;
        });
        decompressNativesEvent.register((sender, value) -> {
            if (value == null)
                return false;
            for (int i = 0; i < value.decompressFiles.length; i++)
                try {
                    CompressingUtils.unzip(value.decompressFiles[i], value.getDecompressTo(), value.extractRules[i]::allow, false);
                } catch (IOException ex) {
                    HMCLog.err("Unable to decompress library: " + value.decompressFiles[i] + " to " + value.getDecompressTo(), ex);
                }
            return true;
        });
    }

}
