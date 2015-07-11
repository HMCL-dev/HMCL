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
package org.jackhuang.hellominecraft.launcher.utils.download;

import org.jackhuang.hellominecraft.C;

/**
 *
 * @author huangyuhui
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
