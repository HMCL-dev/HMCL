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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.service.IMinecraftLoader;
import org.jackhuang.hmcl.core.service.IMinecraftModService;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.event.version.LoadedOneVersionEvent;
import org.jackhuang.hmcl.api.event.version.RefreshedVersionsEvent;
import org.jackhuang.hmcl.api.event.version.RefreshingVersionsEvent;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.install.MinecraftInstallerService;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.core.download.MinecraftDownloadService;
import org.jackhuang.hmcl.api.game.LaunchOptions;
import org.jackhuang.hmcl.core.launch.MinecraftLoader;
import org.jackhuang.hmcl.core.mod.MinecraftModService;
import org.jackhuang.hmcl.core.mod.ModpackManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.ui.MainFrame;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.task.TaskWindow;

/**
 *
 * @author huangyuhui
 */
public class HMCLMinecraftService extends IMinecraftService {

    private Profile p;
    final Map<String, VersionSetting> versionSettings = new HashMap<>();

    public HMCLMinecraftService(Profile p) {
        this.p = p;
        this.provider = new HMCLGameProvider(this);
        provider.initializeMinecraft();
        HMCLApi.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(versionSettings::clear);
        HMCLApi.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerFirst(() -> {
            if (!checkingModpack) {
                checkingModpack = true;
                if (version().getVersionCount() == 0) {
                    File modpack = new File("modpack.zip").getAbsoluteFile();
                    if (modpack.exists())
                        SwingUtilities.invokeLater(() -> {
                            if (TaskWindow.factory().execute(ModpackManager.install(MainFrame.INSTANCE, modpack, this, null)))
                                version().refreshVersions();
                            checkedModpack = true;
                        });
                }
            }
        });
        HMCLApi.EVENT_BUS.channel(LoadedOneVersionEvent.class).register(e -> loadVersionSetting(e.getValue()));
        this.mms = new MinecraftModService(this);
        this.mds = new MinecraftDownloadService(this);
        this.mas = new HMCLAssetService(this);
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
            return;
        initVersionSetting(id, vs);
    }

    public void createVersionSetting(String id) {
        if (provider.getVersionById(id) == null || versionSettings.containsKey(id))
            return;
        initVersionSetting(id, new VersionSetting());
    }

    private void initVersionSetting(String id, VersionSetting vs) {
        vs.id = id;
        vs.propertyChanged.register(event -> saveVersionSetting(((VersionSetting) event.getSource()).id));
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
        return p.getGameDir();
    }

    protected HMCLGameProvider provider;

    @Override
    public HMCLGameProvider version() {
        return provider;
    }

    protected MinecraftModService mms;

    @Override
    public IMinecraftModService mod() {
        return mms;
    }

    protected MinecraftDownloadService mds;

    @Override
    public MinecraftDownloadService download() {
        return mds;
    }

    final HMCLAssetService mas;

    @Override
    public HMCLAssetService asset() {
        return mas;
    }

    protected MinecraftInstallerService mis;

    @Override
    public MinecraftInstallerService install() {
        return mis;
    }

    @Override
    public IMinecraftLoader launch(LaunchOptions options, UserProfileProvider p) throws GameException {
        MinecraftLoader l = new HMCLMinecraftLoader(options, this, p);
        l.setAssetProvider(mas.ASSET_PROVIDER_IMPL);
        return l;
    }

    public Profile getProfile() {
        return p;
    }

}
