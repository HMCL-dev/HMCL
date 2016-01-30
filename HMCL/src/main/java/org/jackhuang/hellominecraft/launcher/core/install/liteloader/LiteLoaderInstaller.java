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
package org.jackhuang.hellominecraft.launcher.core.install.liteloader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.util.tasks.communication.PreviousResultRegistrar;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;

/**
 *
 * @author huangyuhui
 */
public class LiteLoaderInstaller extends Task implements PreviousResultRegistrar<File> {

    public LiteLoaderVersionList.LiteLoaderInstallerVersion version;
    public File installer;
    public String installId;
    public IMinecraftService service;

    public LiteLoaderInstaller(IMinecraftService service, String installId, LiteLoaderVersionList.LiteLoaderInstallerVersion v) {
        this(service, installId, v, null);
    }

    public LiteLoaderInstaller(IMinecraftService service, String installId, LiteLoaderVersionList.LiteLoaderInstallerVersion v, File installer) {
        this.service = service;
        this.installId = installId;
        this.version = v;
        this.installer = installer;
    }

    @Override
    public void executeTask() throws Exception {
        if (installId == null)
            throw new IllegalStateException(C.i18n("install.no_version"));
        if (pre.size() != 1 && installer == null)
            throw new IllegalStateException("No registered previous task.");
        if (installer == null)
            installer = pre.get(pre.size() - 1).getResult();
        MinecraftVersion mv = (MinecraftVersion) service.version().getVersionById(installId).clone();
        mv.inheritsFrom = mv.id;
        mv.jar = mv.jar == null ? mv.id : mv.jar;
        mv.libraries = new ArrayList(Arrays.asList(version.libraries));

        MinecraftLibrary ml = new MinecraftLibrary("com.mumfrey:liteloader:" + version.selfVersion);
        //ml.url = "http://dl.liteloader.com/versions/com/mumfrey/liteloader/" + version.mcVersion + "/liteloader-" + version.selfVersion + ".jar";
        mv.libraries.add(0, ml);
        FileUtils.copyFile(installer, new File(service.baseDirectory(), "libraries/com/mumfrey/liteloader/" + version.selfVersion + "/liteloader-" + version.selfVersion + ".jar"));

        mv.id += "-LiteLoader" + version.selfVersion;

        mv.mainClass = "net.minecraft.launchwrapper.Launch";
        mv.minecraftArguments += " --tweakClass " + version.tweakClass;
        File folder = new File(service.baseDirectory(), "versions/" + mv.id);
        folder.mkdirs();
        File json = new File(folder, mv.id + ".json");
        HMCLog.log("Creating new version profile..." + mv.id + ".json");
        FileUtils.write(json, C.GSON.toJson(mv));
    }

    @Override
    public String getInfo() {
        return C.i18n("install.liteloader.install");
    }

    ArrayList<PreviousResult<File>> pre = new ArrayList<>();

    @Override
    public Task registerPreviousResult(PreviousResult pr) {
        pre.add(pr);
        return this;
    }

}
