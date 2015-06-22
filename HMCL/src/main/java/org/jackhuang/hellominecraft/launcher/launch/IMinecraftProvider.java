/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
