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
package org.jackhuang.hellominecraft.launcher.launch;

import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jackhuang.hellominecraft.launcher.utils.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.utils.settings.Profile;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftVersion;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftProvider {
    Profile profile;

    public IMinecraftProvider(Profile profile) {
        this.profile = profile;
    }
    
    public abstract File getRunDirectory(String id);
    public abstract List<GameLauncher.DownloadLibraryJob> getDownloadLibraries(DownloadType type);
    public abstract void openSelf(String version);
    public abstract void open(String version, String folder);
    public abstract File getAssets();
    public abstract File getResourcePacks();
    public abstract GameLauncher.DecompressLibraryJob getDecompressLibraries();
    public abstract File getDecompressNativesToLocation();
    public abstract File getMinecraftJar();
    public abstract File getBaseFolder();

    /**
     * Launch
     * @param p player informations, including username & auth_token
     * @param type according to the class name 233
     * @return what you want
     * @throws IllegalStateException circular denpendency versions
     */
    public abstract IMinecraftLoader provideMinecraftLoader(UserProfileProvider p, DownloadType type) throws IllegalStateException;
    
    // Versions
    public abstract boolean renameVersion(String from, String to);
    public abstract boolean removeVersionFromDisk(String a);
    public abstract boolean refreshJson(String a);
    public abstract boolean refreshAssetsIndex(String a);
    
    public abstract MinecraftVersion getOneVersion();
    public abstract Collection<MinecraftVersion> getVersions();
    public abstract MinecraftVersion getVersionById(String id);
    public abstract int getVersionCount();
    public abstract void refreshVersions();
    
    public abstract boolean install(String version, DownloadType type);
    
    public abstract void onLaunch();

}
