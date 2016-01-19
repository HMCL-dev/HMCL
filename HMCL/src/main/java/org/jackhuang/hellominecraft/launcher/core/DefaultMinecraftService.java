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
package org.jackhuang.hellominecraft.launcher.core;

import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftDownloadService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftInstallerService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftModService;
import org.jackhuang.hellominecraft.launcher.core.installers.MinecraftInstallerService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftDownloadService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftModService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersionManager;

/**
 *
 * @author huangyuhui
 */
public class DefaultMinecraftService extends IMinecraftService {

    public DefaultMinecraftService(Profile p) {
        super(p);
        this.provider = new MinecraftVersionManager(this);
        provider.initializeMiencraft();
        this.mms = new MinecraftModService(this);
        this.mds = new MinecraftDownloadService(this);
        this.mas = new MinecraftAssetService(this);
        this.mis = new MinecraftInstallerService(this);
    }

    protected IMinecraftProvider provider;

    @Override
    public IMinecraftProvider version() {
        return provider;
    }

    protected MinecraftModService mms;

    @Override
    public IMinecraftModService mod() {
        return mms;
    }

    protected MinecraftDownloadService mds;

    @Override
    public IMinecraftDownloadService download() {
        return mds;
    }

    final MinecraftAssetService mas;

    @Override
    public IMinecraftAssetService asset() {
        return mas;
    }

    protected MinecraftInstallerService mis;

    @Override
    public IMinecraftInstallerService install() {
        return mis;
    }

}
