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
package org.jackhuang.hellominecraft.launcher.util;

import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.install.MinecraftInstallerService;
import org.jackhuang.hellominecraft.launcher.core.asset.MinecraftAssetService;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.download.MinecraftDownloadService;
import org.jackhuang.hellominecraft.launcher.core.launch.LaunchOptions;
import org.jackhuang.hellominecraft.launcher.core.launch.MinecraftLoader;
import org.jackhuang.hellominecraft.launcher.core.service.*;
import org.jackhuang.hellominecraft.launcher.core.mod.MinecraftModService;
import org.jackhuang.hellominecraft.launcher.core.mod.ModpackManager;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.VersionSetting;
import org.jackhuang.hellominecraft.launcher.ui.MainFrame;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.tasks.TaskWindow;

/**
 *
 * @author huangyuhui
 */
public class HMCLMinecraftService extends IMinecraftService {

    Profile p;
    final Map<String, VersionSetting> versionSettings = new HashMap<>();

    public HMCLMinecraftService(Profile p) {
        this.p = p;
        this.provider = new HMCLGameProvider(this);
        provider.initializeMiencraft();
        provider.onRefreshingVersions.register(versionSettings::clear);
        provider.onRefreshedVersions.register(() -> {
            if (!checkingModpack) {
                checkingModpack = true;
                if (version().getVersionCount() == 0) {
                    File modpack = new File("modpack.zip");
                    if (modpack.exists())
                        SwingUtilities.invokeLater(() -> {
                            if (TaskWindow.factory().execute(ModpackManager.install(MainFrame.INSTANCE, modpack, this, null)))
                                version().refreshVersions();
                            checkedModpack = true;
                        });
                }
            }
        });
        provider.onLoadedVersion.register(this::loadVersionSetting);
        this.mms = new MinecraftModService(this);
        this.mds = new MinecraftDownloadService(this);
        this.mas = new MinecraftAssetService(this);
        this.mis = new MinecraftInstallerService(this);
    }

    public boolean checkedModpack = false, checkingModpack = false;

    private void loadVersionSetting(String id) {
        if (provider.getVersionById(id) == null)
            return;
        VersionSetting vs = null;
        File f = new File(provider.versionRoot(id), "hmclversion.cfg");
        if (f.exists()) {
            String s = FileUtils.readQuietly(f);
            if (s != null)
                try {
                    vs = C.GSON.fromJson(s, VersionSetting.class);
                } catch (JsonSyntaxException ex) {
                    HMCLog.warn("Failed to load version setting: " + id, ex);
                    vs = null;
                }
        }
        if (vs == null)
            vs = new VersionSetting();
        vs.id = id;
        vs.propertyChanged.register((sender, t) -> {
            saveVersionSetting(((VersionSetting) sender).id);
            return true;
        });
        versionSettings.put(id, vs);
    }

    /**
     * Get the version setting for version id.
     *
     * @param id version id
     *
     * @return may return null if the id not exists
     */
    public VersionSetting getVersionSetting(String id) {
        if (!versionSettings.containsKey(id))
            loadVersionSetting(id);
        return versionSettings.get(id);
    }

    public void saveVersionSetting(String id) {
        if (!versionSettings.containsKey(id))
            return;
        File f = new File(provider.versionRoot(id), "hmclversion.cfg");
        FileUtils.writeQuietly(f, C.GSON.toJson(versionSettings.get(id)));
    }

    @Override
    public File baseDirectory() {
        return p.getCanonicalGameDirFile();
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

    @Override
    public IMinecraftLoader launch(LaunchOptions options, UserProfileProvider p) throws GameException {
        MinecraftLoader l = new MinecraftLoader(options, this, p);
        l.setAssetProvider(mas.ASSET_PROVIDER_IMPL);
        return l;
    }

    public Profile getProfile() {
        return p;
    }

}
