/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.setting;

import javafx.beans.binding.Bindings;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.util.CacheRepository;
import org.jackhuang.hmcl.util.io.FileUtils;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

public final class Settings {

    private static Settings instance;

    public static Settings instance() {
        if (instance == null) {
            throw new IllegalStateException("Settings hasn't been initialized");
        }
        return instance;
    }

    /// Initializes process-wide setting modules.
    static void init() {
        instance = new Settings();
    }

    private Settings() {
        DownloadProviders.init();
        ProxyManager.init();
        Accounts.init();
        Profiles.init();
        AuthlibInjectorServers.init();
        AnimationUtils.init();

        CacheRepository.setInstance(HMCLCacheRepository.REPOSITORY);
        HMCLCacheRepository.REPOSITORY.directoryProperty().bind(Bindings.createStringBinding(() -> {
            if (FileUtils.canCreateDirectory(getCommonDirectory())) {
                return getCommonDirectory();
            } else {
                return getDefaultCommonDirectory();
            }
        }, settings().commonDirectoryProperty(), settings().commonDirTypeProperty()));
    }

    public static String getDefaultCommonDirectory() {
        return Metadata.MINECRAFT_DIRECTORY.toString();
    }

    public String getCommonDirectory() {
        switch (settings().commonDirTypeProperty().get()) {
            case DEFAULT:
                return getDefaultCommonDirectory();
            case CUSTOM:
                return settings().commonDirectoryProperty().get();
            default:
                return null;
        }
    }
}
