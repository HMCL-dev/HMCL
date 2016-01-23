/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.utils.upgrade;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraft.utils.logging.HMCLog;
import org.jackhuang.hellominecraft.utils.tasks.TaskWindow;
import org.jackhuang.hellominecraft.utils.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class NewFileUpgrader extends IUpgrader {

    @Override
    public boolean parseArguments(VersionNumber nowVersion, String[] args) {
        int i = ArrayUtils.indexOf(args, "--removeOldLauncher");
        if (i != -1 && i < args.length - 1) {
            File f = new File(args[i + 1]);
            if (f.exists())
                f.deleteOnExit();
        }
        return false;
    }

    @Override
    public boolean call(Object sender, VersionNumber number) {
        String str = requestDownloadLink();
        File newf = new File(FileUtils.getName(str));
        if (TaskWindow.getInstance().addTask(new FileDownloadTask(str, newf)).start()) {
            try {
                new ProcessBuilder(new String[] { IOUtils.tryGetCanonicalFilePath(newf), "--removeOldLauncher", IOUtils.getRealPath() }).directory(new File(".")).start();
            } catch (IOException ex) {
                HMCLog.err("Failed to start new app", ex);
            }
            System.exit(0);
        }
        return true;
    }

    private String requestDownloadLink() {
        return null;
    }

}
