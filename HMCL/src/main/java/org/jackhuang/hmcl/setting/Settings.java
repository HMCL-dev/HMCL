/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.setting;

import javafx.beans.value.ObservableValue;
import javafx.scene.text.Font;

import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.event.*;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

public class Settings {

    private static Settings instance;

    public static Settings instance() {
        if (instance == null) {
            throw new IllegalStateException("Settings hasn't been initialized");
        }
        return instance;
    }

    /**
     * Should be called from {@link ConfigHolder#init()}.
     */
    static void init() {
        instance = new Settings();
    }

    private Settings() {
        ProxyManager.init();
        Accounts.init();
        Profiles.init();
    }

    public Font getFont() {
        return Font.font(config().getFontFamily(), config().getFontSize());
    }

    public void setFont(Font font) {
        config().setFontFamily(font.getFamily());
        config().setFontSize(font.getSize());
    }

    public int getLogLines() {
        return Math.max(config().getLogLines(), 100);
    }

    public void setLogLines(int logLines) {
        config().setLogLines(logLines);
    }

    public boolean isCommonDirectoryDisabled() {
        return config().getCommonDirType() == EnumCommonDirectory.DISABLED;
    }

    public static String getDefaultCommonDirectory() {
        return Launcher.MINECRAFT_DIRECTORY.getAbsolutePath();
    }

    public String getCommonDirectory() {
        switch (config().getCommonDirType()) {
            case DISABLED:
                return null;
            case DEFAULT:
                return getDefaultCommonDirectory();
            case CUSTOM:
                return config().getCommonDirectory();
            default:
                return null;
        }
    }

    /****************************************
     *        DOWNLOAD PROVIDERS            *
     ****************************************/

    public DownloadProvider getDownloadProvider() {
        return DownloadProviders.getDownloadProvider(config().getDownloadType());
    }

    public void setDownloadProvider(DownloadProvider downloadProvider) {
        int index = DownloadProviders.DOWNLOAD_PROVIDERS.indexOf(downloadProvider);
        if (index == -1)
            throw new IllegalArgumentException("Unknown download provider: " + downloadProvider);
        config().setDownloadType(index);
    }
}
