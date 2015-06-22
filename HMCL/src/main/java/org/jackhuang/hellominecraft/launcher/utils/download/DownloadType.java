/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.download;

import org.jackhuang.hellominecraft.C;

/**
 *
 * @author hyh
 */
public enum DownloadType {
    Mojang(C.i18n("download.mojang"), new MojangDownloadProvider()),
    BMCL(C.i18n("download.BMCL"), new BMCLAPIDownloadProvider());
    
    private final String name;
    private final IDownloadProvider provider;
    
    DownloadType(String a, IDownloadProvider provider) {
        name = a;
        this.provider = provider;
    }

    public IDownloadProvider getProvider() {
        return provider;
    }
    
    public String getName() {
        return name;
    }
}
