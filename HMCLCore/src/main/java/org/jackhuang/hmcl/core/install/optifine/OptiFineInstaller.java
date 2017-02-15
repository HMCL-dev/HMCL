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
package org.jackhuang.hmcl.core.install.optifine;

import java.io.File;
import java.util.ArrayList;
import java.util.zip.ZipFile;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.version.MinecraftLibraryPathEvent;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.core.install.InstallerVersionList;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.version.LibrariesDownloadInfo;
import org.jackhuang.hmcl.core.version.LibraryDownloadInfo;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.task.comm.PreviousResult;
import org.jackhuang.hmcl.util.task.comm.PreviousResultRegistrar;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.core.version.MinecraftLibrary;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.api.Wrapper;
import org.jackhuang.hmcl.api.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class OptiFineInstaller extends Task implements PreviousResultRegistrar<File> {

    public File installer;
    public IMinecraftService service;
    public InstallerVersionList.InstallerVersion version;
    public String installId;

    public OptiFineInstaller(IMinecraftService service, String installId, InstallerVersionList.InstallerVersion version, File installer) {
        this.service = service;
        this.installId = installId;
        this.installer = installer;
        this.version = version;
    }

    @Override
    public void executeTask(boolean areDependTasksSucceeded) throws Exception {
        if (installId == null)
            throw new Exception(C.i18n("install.no_version"));
        String selfId = version.selfVersion;
        MinecraftVersion mv = (MinecraftVersion) service.version().getVersionById(installId).clone();
        mv.inheritsFrom = mv.id;
        mv.jar = mv.jar == null ? mv.id : mv.jar;
        mv.libraries.clear();
        MinecraftLibrary library = new MinecraftLibrary("optifine:OptiFine:" + selfId);
        library.downloads = new LibrariesDownloadInfo();
        library.downloads.artifact = new LibraryDownloadInfo();
        library.downloads.artifact.path = "optifine/OptiFine/" + selfId + "/OptiFine-" + selfId + ".jar";
        library.downloads.artifact.url = version.universal;
        library.downloads.artifact.sha1 = null;
        library.downloads.artifact.size = 0;
        mv.libraries.add(0, library);
        
        MinecraftLibraryPathEvent event = new MinecraftLibraryPathEvent(this, "libraries/" + library.downloads.artifact.path, new Wrapper<>(new File(service.baseDirectory(), "libraries/" + library.downloads.artifact.path)));
        HMCLApi.EVENT_BUS.fireChannel(event);
        FileUtils.copyFile(installer, event.getFile().getValue());

        mv.id += "-" + selfId;
        try (ZipFile zipFile = new ZipFile(installer)) {
            if (zipFile.getEntry("optifine/OptiFineTweaker.class") != null) {
                if (!mv.mainClass.startsWith("net.minecraft.launchwrapper.")) {
                    mv.mainClass = "net.minecraft.launchwrapper.Launch";
                    mv.libraries.add(1, new MinecraftLibrary("net.minecraft:launchwrapper:1.7"));
                }
                if (!mv.minecraftArguments.contains("FMLTweaker"))
                    mv.minecraftArguments += " --tweakClass optifine.OptiFineTweaker";
            }
        }
        File loc = new File(service.baseDirectory(), "versions/" + mv.id);
        if (!FileUtils.makeDirectory(loc))
            HMCLog.warn("Failed to make directories: " + loc);
        File json = new File(loc, mv.id + ".json");
        FileUtils.write(json, C.GSON.toJson(mv, MinecraftVersion.class));

        service.version().refreshVersions();
    }

    @Override
    public String getInfo() {
        return "OptiFine Installer";
    }

    ArrayList<PreviousResult<File>> pre = new ArrayList<>();

    @Override
    public Task registerPreviousResult(PreviousResult<File> pr) {
        pre.add(pr);
        return this;
    }

}
