/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.liteloader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResult;
import org.jackhuang.hellominecraft.tasks.communication.PreviousResultRegistrator;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftVersion;

/**
 *
 * @author hyh
 */
public class LiteLoaderInstaller extends Task implements PreviousResultRegistrator<File> {

    public LiteLoaderVersionList.LiteLoaderInstallerVersion version;
    public File installer;
    public Profile profile;

    public LiteLoaderInstaller(Profile profile, LiteLoaderVersionList.LiteLoaderInstallerVersion v) {
        this(profile, v, null);
    }

    public LiteLoaderInstaller(Profile profile, LiteLoaderVersionList.LiteLoaderInstallerVersion v, File installer) {
        this.profile = profile;
        this.version = v;
        this.installer = installer;
    }

    @Override
    public boolean executeTask() {
        if(profile == null || profile.getSelectedMinecraftVersion() == null) {
            setFailReason(new RuntimeException(C.i18n("install.no_version")));
            return false;
        }
        if(pre.size() != 1 && installer == null) throw new IllegalStateException("No registered previous task.");
        if(installer == null)
            installer = pre.get(pre.size() - 1).getResult();
        try {
            MinecraftVersion mv = (MinecraftVersion) profile.getSelectedMinecraftVersion().clone();
            mv.inheritsFrom = mv.id;
            mv.jar = mv.jar == null ? mv.id : mv.jar;
            mv.libraries = new ArrayList(Arrays.asList(version.libraries));
            
            mv.libraries.add(0, new MinecraftLibrary("com.mumfrey:liteloader:" + version.selfVersion));
            FileUtils.copyFile(installer, new File(profile.getCanonicalGameDir(), "libraries/com/mumfrey/liteloader/" + version.selfVersion + "/liteloader-" + version.selfVersion + ".jar"));

            mv.id += "-LiteLoader" + version.selfVersion;

            if(!mv.mainClass.startsWith("net.minecraft.launchwrapper.")) {
                mv.mainClass = "net.minecraft.launchwrapper.Launch";
                mv.minecraftArguments += " --tweakClass " + version.tweakClass;
            }
            File folder = new File(profile.getCanonicalGameDir(), "versions/" + mv.id);
            folder.mkdirs();
            File json = new File(folder, mv.id + ".json");
            HMCLog.log("Creating new version profile..." + mv.id + ".json");
            FileUtils.write(json, C.gsonPrettyPrinting.toJson(mv));
            
            return true;
        } catch (Exception e) {
            setFailReason(e);
            return false;
        }
    }

    @Override
    public String getInfo() {
        return C.i18n("install.liteloader.install");
    }
    
    ArrayList<PreviousResult<File>> pre = new ArrayList();
    @Override
    public Task registerPreviousResult(PreviousResult pr) {
        pre.add(pr);
        return this;
    }

}
