/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.mod.CurseManifest;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.MultiMCInstanceConfiguration;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.util.Lang;

import java.io.File;
import java.util.Optional;

public final class ModpackHelper {
    private ModpackHelper() {}

    public static Modpack readModpackManifest(File file) {
        try {
            return CurseManifest.readCurseForgeModpackManifest(file);
        } catch (Exception e) {
            // ignore it, not a valid CurseForge modpack.
        }

        try {
            return HMCLModpackManager.readHMCLModpackManifest(file);
        } catch (Exception e) {
            // ignore it, not a valid HMCL modpack.
        }

        try {
            return MultiMCInstanceConfiguration.readMultiMCModpackManifest(file);
        } catch (Exception e) {
            // ignore it, not a valid MultiMC modpack.
        }

        throw new IllegalArgumentException("Modpack file " + file + " is not supported.");
    }

    public static void toVersionSetting(MultiMCInstanceConfiguration c, VersionSetting vs) {
        vs.setUsesGlobal(false);
        vs.setGameDirType(EnumGameDirectory.VERSION_FOLDER);

        if (c.isOverrideJavaLocation()) {
            vs.setJavaDir(Lang.nonNull(c.getJavaPath(), ""));
        }

        if (c.isOverrideMemory()) {
            vs.setPermSize(Optional.ofNullable(c.getPermGen()).map(i -> i.toString()).orElse(""));
            if (c.getMaxMemory() != null)
                vs.setMaxMemory(c.getMaxMemory());
            vs.setMinMemory(c.getMinMemory());
        }

        if (c.isOverrideCommands()) {
            vs.setWrapper(Lang.nonNull(c.getWrapperCommand(), ""));
            vs.setPreLaunchCommand(Lang.nonNull(c.getPreLaunchCommand(), ""));
        }

        if (c.isOverrideJavaArgs()) {
            vs.setJavaArgs(Lang.nonNull(c.getJvmArgs(), ""));
        }

        if (c.isOverrideConsole()) {
            vs.setShowLogs(c.isShowConsole());
        }

        if (c.isOverrideWindow()) {
            vs.setFullscreen(c.isFullscreen());
            if (c.getWidth() != null)
                vs.setWidth(c.getWidth());
            if (c.getHeight() != null)
                vs.setHeight(c.getHeight());
        }
    }
}
