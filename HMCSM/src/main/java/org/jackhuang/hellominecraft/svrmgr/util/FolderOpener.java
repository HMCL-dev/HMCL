/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.util;

import java.io.File;
import org.jackhuang.hellominecraft.util.MessageBox;

/**
 *
 * @author jack
 */
public class FolderOpener {

    public static void open(String s) {
        try {
            File f = new File(s);
            f.mkdirs();
            java.awt.Desktop.getDesktop().open(f);
        } catch (Exception ex) {
            MessageBox.show("无法打开资源管理器: " + ex.getMessage());
        }
    }

    public static void openResourcePacks(String gameDir) {
        open(gameDir + "resourcepacks");
    }

    public static void openTextutrePacks(String gameDir) {
        open(gameDir + "texturepacks");
    }

    public static void openMods() {
        open(Utilities.try2GetPath("mods"));
    }

    public static void openCoreMods() {
        open(Utilities.try2GetPath("coremods"));
    }

    public static void openPlugins() {
        open(Utilities.try2GetPath("plugins"));
    }

    public static void openConfig() {
        open(Utilities.try2GetPath("config"));
    }

}
