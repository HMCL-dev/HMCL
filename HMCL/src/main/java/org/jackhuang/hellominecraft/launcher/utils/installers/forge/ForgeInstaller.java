/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.forge;

import org.jackhuang.hellominecraft.launcher.utils.installers.InstallProfile;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.launcher.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.utils.system.MessageBox;

/**
 *
 * @author hyh
 */
public class ForgeInstaller extends Task {

    private final Gson gson = new Gson();
    public File gameDir;
    public File forgeInstaller;
    public IMinecraftProvider mp;

    public ForgeInstaller(IMinecraftProvider mp, File forgeInstaller) {
	this.gameDir = mp.getBaseFolder();
	this.forgeInstaller = forgeInstaller;
        this.mp = mp;
    }

    @Override
    public boolean executeTask() {
        try {
            HMCLog.log("Extracting install profiles...");

            ZipFile zipFile = new ZipFile(forgeInstaller);
            ZipEntry entry = zipFile.getEntry("install_profile.json");
            String content = NetUtils.getStreamContent(zipFile.getInputStream(entry));
            InstallProfile profile = gson.fromJson(content, InstallProfile.class);

            File from = new File(gameDir, "versions" + File.separator + profile.install.minecraft);
            if(!from.exists()) {
                if(MessageBox.Show(C.i18n("install.no_version_if_intall")) == MessageBox.YES_OPTION) {
                    if(!mp.install(profile.install.minecraft, Settings.getInstance().getDownloadSource())) {
                        setFailReason(new RuntimeException(C.i18n("install.no_version")));
                    }
                } else {
                    setFailReason(new RuntimeException(C.i18n("install.no_version")));
                }
                return false;
            }
            
            File to = new File(gameDir, "versions" + File.separator + profile.install.target);
            to.mkdirs();

            HMCLog.log("Copying jar..." + profile.install.minecraft + ".jar to " + profile.install.target + ".jar");
            FileUtils.copyFile(new File(from, profile.install.minecraft + ".jar"),
                    new File(to, profile.install.target + ".jar"));
            HMCLog.log("Creating new version profile..." + profile.install.target + ".json");
            FileUtils.write(new File(to, profile.install.target + ".json"), gson.toJson(profile.versionInfo));

            HMCLog.log("Extracting universal forge pack..." + profile.install.filePath);

            entry = zipFile.getEntry(profile.install.filePath);
            InputStream is = zipFile.getInputStream(entry);

            MinecraftLibrary forge = new MinecraftLibrary(profile.install.path);
            forge.init();
            File file = new File(gameDir, "libraries/" + forge.formatted);
            file.getParentFile().mkdirs();
            try (FileOutputStream fos = new FileOutputStream(file)) {
                BufferedOutputStream bos = new BufferedOutputStream(fos);
                int c;
                while ((c = is.read()) != -1) bos.write((byte) c);
                bos.close();
            }
            return true;
        } catch(IOException | JsonSyntaxException e) {
            setFailReason(e);
            return false;
        }
    }

    @Override
    public String getInfo() {
        return C.i18n("install.forge.install");
    }

}
