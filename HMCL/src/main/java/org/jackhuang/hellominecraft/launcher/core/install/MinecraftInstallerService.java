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
package org.jackhuang.hellominecraft.launcher.core.install;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftInstallerService;
import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.core.install.forge.ForgeInstaller;
import org.jackhuang.hellominecraft.launcher.core.install.liteloader.LiteLoaderInstaller;
import org.jackhuang.hellominecraft.launcher.core.install.liteloader.LiteLoaderVersionList;
import org.jackhuang.hellominecraft.launcher.core.install.optifine.OptiFineInstaller;
import org.jackhuang.hellominecraft.launcher.core.install.optifine.vanilla.OptiFineDownloadFormatter;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;
import org.jackhuang.hellominecraft.util.tasks.TaskWindow;
import org.jackhuang.hellominecraft.util.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public final class MinecraftInstallerService extends IMinecraftInstallerService {

    public MinecraftInstallerService(IMinecraftService service) {
        super(service);
    }

    @Override
    public Task download(String installId, InstallerVersion v, InstallerType type) {
        switch (type) {
        case Forge:
            return downloadForge(installId, v);
        case OptiFine:
            return downloadOptiFine(installId, v);
        case LiteLoader:
            return downloadLiteLoader(installId, v);
        default:
            return null;
        }
    }

    @Override
    public Task downloadForge(String installId, InstallerVersion v) {
        return new TaskInfo("Forge Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "forge-installer.jar");
                if (v.installer != null)
                    TaskWindow.factory()
                        .append(new FileDownloadTask(service.getDownloadType().getProvider().getParsedDownloadURL(v.installer), filepath).setTag("forge"))
                        .append(new ForgeInstaller(service, filepath))
                        .create();
            }
        };
    }

    @Override
    public Task downloadOptiFine(String installId, InstallerVersion v) {
        return new TaskInfo("OptiFine Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "optifine-installer.jar");
                if (v.installer != null) {
                    OptiFineDownloadFormatter task = new OptiFineDownloadFormatter(v.installer);
                    TaskWindow.factory().append(task)
                        .append(new FileDownloadTask(filepath).registerPreviousResult(task).setTag("optifine"))
                        .append(new OptiFineInstaller(service, installId, v, filepath))
                        .create();
                }
            }
        };
    }

    @Override
    public Task downloadLiteLoader(String installId, InstallerVersion v) {
        return new TaskInfo("LiteLoader Downloader") {
            @Override
            public void executeTask() {
                File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "liteloader-universal.jar");
                FileDownloadTask task = (FileDownloadTask) new FileDownloadTask(v.universal, filepath).setTag("LiteLoader");
                TaskWindow.factory()
                    .append(task).append(new LiteLoaderInstaller(service, installId, (LiteLoaderVersionList.LiteLoaderInstallerVersion) v).registerPreviousResult(task))
                    .create();
            }
        };
    }
}
