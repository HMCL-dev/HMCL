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
package org.jackhuang.hellominecraft.launcher.utils.installers;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.utils.installers.forge.ForgeInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.OptiFineInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla.OptiFineDownloadFormatter;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.TaskInfo;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public final class InstallerService {

    Profile p;

    public InstallerService(Profile p) {
        this.p = p;
    }
    
    public Task download(InstallerVersion v, InstallerType type) {
        switch(type) {
            case Forge:
                return downloadForge(v);
            case Optifine:
                return downloadOptifine(v);
            case LiteLoader:
                return downloadLiteLoader(v);
            default:
                return null;
        }
    }

    public Task downloadForge(InstallerVersion v) {
        return new TaskInfo("Forge Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "forge-installer.jar");
                if (v.installer != null)
                    TaskWindow.getInstance()
                    .addTask(new FileDownloadTask(p.getDownloadType().getProvider().getParsedLibraryDownloadURL(v.installer), filepath).setTag("forge"))
                    .addTask(new ForgeInstaller(p.getMinecraftProvider(), filepath, v))
                    .start();
            }
        };
    }

    public Task downloadOptifine(InstallerVersion v) {
        return new TaskInfo("OptiFine Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "optifine-installer.jar");
                if (v.installer != null) {
                    OptiFineDownloadFormatter task = new OptiFineDownloadFormatter(v.installer);
                    TaskWindow.getInstance().addTask(task)
                    .addTask(new FileDownloadTask(filepath).registerPreviousResult(task).setTag("optifine"))
                    .addTask(new OptiFineInstaller(p, v.selfVersion, filepath))
                    .start();
                }
            }
        };
    }

    public Task downloadLiteLoader(InstallerVersion v) {
        return new TaskInfo("LiteLoader Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "liteloader-universal.jar");
                FileDownloadTask task = (FileDownloadTask) new FileDownloadTask(v.universal, filepath).setTag("LiteLoader");
                TaskWindow.getInstance()
                .addTask(task).addTask(new LiteLoaderInstaller(p, (LiteLoaderVersionList.LiteLoaderInstallerVersion) v).registerPreviousResult(task))
                .start();
            }
        };
    }
}
