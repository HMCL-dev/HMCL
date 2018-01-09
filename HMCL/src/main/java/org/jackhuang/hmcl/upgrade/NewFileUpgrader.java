/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.MainKt;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.util.Charsets;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 *
 * @author huangyuhui
 */
public class NewFileUpgrader extends IUpgrader {

    @Override
    public void parseArguments(VersionNumber nowVersion, List<String> args) {
        int i = args.indexOf("--removeOldLauncher");
        if (i != -1 && i < args.size() - 1) {
            File f = new File(args.get(i + 1));
            if (f.exists())
                f.deleteOnExit();
        }
    }

    @Override
    public void download(UpdateChecker checker, VersionNumber version) {
        URL url = requestDownloadLink();
        if (url == null) return;
        File newf = new File(url.getFile());
        Controllers.INSTANCE.dialog(MainKt.i18n("ui.message.downloading"));
        if (new FileDownloadTask(url, newf).test()) {
            try {
                new ProcessBuilder(newf.getCanonicalPath(), "--removeOldLauncher", getRealPath())
                        .directory(new File("").getAbsoluteFile())
                        .start();
            } catch (IOException ex) {
                Logging.LOG.log(Level.SEVERE, "Failed to start new app", ex);
            }
            System.exit(0);
        }
        Controllers.INSTANCE.closeDialog();
    }

    private static String getRealPath() {
        String realPath = NewFileUpgrader.class.getClassLoader().getResource("").getFile();
        File file = new File(realPath);
        realPath = file.getAbsolutePath();
        try {
            realPath = java.net.URLDecoder.decode(realPath, Charsets.DEFAULT_CHARSET.name());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return realPath;
    }

    private URL requestDownloadLink() {
        return null;
    }

}

