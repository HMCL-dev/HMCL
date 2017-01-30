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
package org.jackhuang.hellominecraft.svrmgr.setting;

/**
 *
 * @author huangyuhui
 */
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.FileUtils;

/**
 *
 * @author huangyuhui
 */
public class SettingsManager {

    public static Settings settings;
    public static boolean isFirstLoad = false;
    static Gson gson;
    private static final File file = new File("hmcsm.json");

    public static void load() {
        gson = new Gson();
        if (file.exists())
            try {
                String str = FileUtils.read(file);
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
        try {
            FileUtils.write(file, gson.toJson(settings));
        } catch (IOException ex) {
            HMCLog.err("Failed to save settings.", ex);
        }
    }
}
