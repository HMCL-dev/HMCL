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
package org.jackhuang.hellominecraft.launcher.utils.installers.optifine;

import java.io.File;
import java.util.ArrayList;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResultRegistrar;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.launcher.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;

/**
 *
 * @author huangyuhui
 */
public class OptiFineInstaller extends Task implements PreviousResultRegistrar<File> {

    public File installer;
    public Profile profile;
    public String version;

    public OptiFineInstaller(Profile profile, String version) {
        this(profile, version, null);
    }

    public OptiFineInstaller(Profile profile, String version, File installer) {
        this.profile = profile;
        this.installer = installer;
        this.version = version;
    }

    @Override
    public void executeTask() throws Exception {
        if (profile == null || profile.getMinecraftProvider().getSelectedVersion() == null)
            throw new Exception(C.i18n("install.no_version"));
        MinecraftVersion mv = (MinecraftVersion) profile.getMinecraftProvider().getSelectedVersion().clone();
        mv.inheritsFrom = mv.id;
        mv.jar = mv.jar == null ? mv.id : mv.jar;
        mv.libraries.clear();
        mv.libraries.add(0, new MinecraftLibrary("optifine:OptiFine:" + version));
        FileUtils.copyFile(installer, new File(profile.getCanonicalGameDir(), "libraries/optifine/OptiFine/" + version + "/OptiFine-" + version + ".jar"));

        mv.id += "-" + version;
        if (new ZipFile(installer).getEntry("optifine/OptiFineTweaker.class") != null) {
            if (!mv.mainClass.startsWith("net.minecraft.launchwrapper.")) {
                mv.mainClass = "net.minecraft.launchwrapper.Launch";
                mv.libraries.add(1, new MinecraftLibrary("net.minecraft:launchwrapper:1.7"));
            }
            mv.minecraftArguments += " --tweakClass optifine.OptiFineTweaker";
        }
        File loc = new File(profile.getCanonicalGameDir(), "versions/" + mv.id);
        loc.mkdirs();
        File json = new File(loc, mv.id + ".json");
        FileUtils.writeStringToFile(json, C.gsonPrettyPrinting.toJson(mv, MinecraftVersion.class));
    }

    @Override
    public String getInfo() {
        return "Optifine Installer";
    }

    ArrayList<PreviousResult<File>> pre = new ArrayList();

    @Override
    public Task registerPreviousResult(PreviousResult pr) {
        pre.add(pr);
        return this;
    }

}
