/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.download;

import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;

/**
 *
 * @author huangyuhui
 */
public class MojangDownloadProvider implements IDownloadProvider {

    @Override
    public InstallerVersionList getForgeInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla.MinecraftForgeVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getLiteLoaderInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getOptiFineInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla.OptiFineVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getInstallerByType(String type) {
        if(type.equalsIgnoreCase("forge")) return getForgeInstaller();
        if(type.equalsIgnoreCase("liteloader")) return getLiteLoaderInstaller();
        if(type.equalsIgnoreCase("optifine")) return getOptiFineInstaller();
        return null;
    }

    @Override
    public String getLibraryDownloadURL() {
        return "https://libraries.minecraft.net";
    }

    @Override
    public String getVersionsDownloadURL() {
        return "https://s3.amazonaws.com/Minecraft.Download/versions/";
    }

    @Override
    public String getIndexesDownloadURL() {
        return "https://s3.amazonaws.com/Minecraft.Download/indexes/";
    }

    @Override
    public String getVersionsListDownloadURL() {
        return "https://s3.amazonaws.com/Minecraft.Download/versions/versions.json";
    }

    @Override
    public String getAssetsDownloadURL() {
        return "http://resources.download.minecraft.net/";
    }

    @Override
    public boolean isAllowedToUseSelfURL() {
        return true;
    }
    
}
