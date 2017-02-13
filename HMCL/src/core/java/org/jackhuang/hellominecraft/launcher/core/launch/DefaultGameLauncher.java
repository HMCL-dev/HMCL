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
import java.util.List;
import org.jackhuang.hellominecraft.api.HMCAPI;
import org.jackhuang.hellominecraft.api.ResultedSimpleEvent;
import org.jackhuang.hellominecraft.launcher.api.event.launch.DecompressLibrariesEvent;
import org.jackhuang.hellominecraft.launcher.api.event.launch.DownloadLibrariesEvent;
import org.jackhuang.hellominecraft.launcher.api.event.launch.DownloadLibraryJob;
import org.jackhuang.hellominecraft.launcher.core.auth.IAuthenticator;
import org.jackhuang.hellominecraft.launcher.core.auth.LoginInfo;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.CompressingUtils;
import org.jackhuang.hellominecraft.util.task.ParallelTask;
import org.jackhuang.hellominecraft.util.task.TaskWindow;


public class DefaultGameLauncher extends GameLauncher {

    public DefaultGameLauncher(LaunchOptions options, IMinecraftService service, LoginInfo info, IAuthenticator lg) {
        super(options, service, info, lg);
        register();
    }

    private void register() {
        HMCAPI.EVENT_BUS.channel(DownloadLibrariesEvent.class).register(t -> {
            ResultedSimpleEvent<List<DownloadLibraryJob>> event = (ResultedSimpleEvent) t;
            final TaskWindow.TaskWindowFactory dw = TaskWindow.factory();
            ParallelTask parallelTask = new ParallelTask();
            HashSet<String> names = new HashSet<>();
            for (DownloadLibraryJob s : t.getValue()) {
                if (names.contains(s.lib.name))
                    continue;
                names.add(s.lib.name);
                parallelTask.addTask(new LibraryDownloadTask(s));
            }
            dw.append(parallelTask);
            boolean flag = true;
            if (t.getValue().size() > 0)
                flag = dw.execute();
            if (!flag && MessageBox.show(C.i18n("launch.not_finished_downloading_libraries"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                flag = true;
            t.setResult(flag);
        });
        HMCAPI.EVENT_BUS.channel(DecompressLibrariesEvent.class).register(t -> {
            if (t.getValue() == null) {
                t.setResult(false);
                return;
            }
            for (int i = 0; i < t.getValue().decompressFiles.length; i++)
                try {
                    CompressingUtils.unzip(t.getValue().decompressFiles[i], t.getValue().getDecompressTo(), t.getValue().extractRules[i]::allow, false);
                } catch (IOException ex) {
                    HMCLog.err("Unable to decompress library: " + t.getValue().decompressFiles[i] + " to " + t.getValue().getDecompressTo(), ex);
                }
        });
    }

}
