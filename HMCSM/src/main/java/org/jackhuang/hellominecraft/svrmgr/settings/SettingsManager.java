/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.svrmgr.settings;

/**
 *
 * @author huangyuhui
 */
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class SettingsManager {

    public static Settings settings;
    public static boolean isFirstLoad = false;
    static Gson gson;

    public static void load() {
        gson = new Gson();
        File file = new File(IOUtils.currentDir(), "hmcsm.json");
        if (file.exists())
            try {
                String str = FileUtils.readFileToString(file);
                if (str == null || str.trim().equals(""))
                    init();
                else
                    settings = gson.fromJson(str, Settings.class);
            } catch (IOException ex) {
                init();
            }
        else {
            settings = new Settings();
            save();
        }
    }

    public static void init() {
        settings = new Settings();
        isFirstLoad = true;
        save();
    }

    public static void save() {
        File f = new File(IOUtils.currentDir(), "hmcsm.json");
        try {
            FileUtils.write(f, gson.toJson(settings));
        } catch (IOException ex) {
            HMCLog.err("Failed to save settings.", ex);
        }
    }
}
