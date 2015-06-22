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
public class BMCLAPIDownloadProvider implements IDownloadProvider {
    
    @Override
    public InstallerVersionList getForgeInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.forge.bmcl.ForgeBMCLVersionList.getInstance();
    }
    
    @Override
    public InstallerVersionList getLiteLoaderInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionList.getInstance();
    }
    
    @Override
    public InstallerVersionList getOptiFineInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.optifine.bmcl.OptiFineBMCLVersionList.getInstance();
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
        return "http://bmclapi2.bangbang93.com/libraries";
    }

    @Override
    public String getVersionsDownloadURL() {
        return "http://bmclapi2.bangbang93.com/versions/";
    }

    @Override
    public String getIndexesDownloadURL() {
        return "http://bmclapi2.bangbang93.com/indexes/";
    }

    @Override
    public String getVersionsListDownloadURL() {
        return  "http://bmclapi2.bangbang93.com/versions/versions.json";
    }

    @Override
    public String getAssetsDownloadURL() {
        return  "http://bmclapi2.bangbang93.com/assets/";
    }

    @Override
    public boolean isAllowedToUseSelfURL() {
        return false;
    }
    
}
