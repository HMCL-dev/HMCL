/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.download;

import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;

/**
 *
 * @author huangyuhui
 */
public interface IDownloadProvider {
    InstallerVersionList getInstallerByType(String type);
    InstallerVersionList getForgeInstaller();
    InstallerVersionList getLiteLoaderInstaller();
    InstallerVersionList getOptiFineInstaller();
    String getLibraryDownloadURL();
    String getVersionsDownloadURL();
    String getIndexesDownloadURL();
    String getVersionsListDownloadURL();
    String getAssetsDownloadURL();
    boolean isAllowedToUseSelfURL();
}
