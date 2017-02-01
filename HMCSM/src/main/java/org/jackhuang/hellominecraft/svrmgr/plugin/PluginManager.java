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
package org.jackhuang.hellominecraft.svrmgr.plugin;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.ho.yaml.Yaml;
import org.jackhuang.hellominecraft.util.net.NetUtils;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class PluginManager {

    public static PluginInformation getPluginYML(File f) {
        try {
            ZipFile file = new ZipFile(f);
            ZipEntry plg = file.getEntry("plugin.yml");
            InputStream is = file.getInputStream(plg);
            return Yaml.loadType(is, PluginInformation.class);
        } catch (Exception ex) {
            Logger.getLogger(PluginManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public static List<BukkitPlugin> getPlugins() throws Exception {
        String result = NetUtils.get("http://api.bukget.org/3//plugins?fields=slug,plugin_name,description,versions.version,versions.game_versions");
        Gson gson = new Gson();
        List<BukkitPlugin> list = gson.fromJson(result, new TypeToken<List<BukkitPlugin>>() {
                                            }.getType());
        return list;
    }

    public static final String CATEGORY_ADMIN_TOOLS = "Admin Tools",
        CATEGORY_DEVELOPER_TOOLS = "Developer Tools",
        CATEGORY_FUN = "Fun",
        CATEGORY_GENERAL = "General",
        CATEGORY_ANTI_GRIEFING_TOOLS = "Anti Griefing Tools",
        CATEGORY_MECHAICS = "Mechanics",
        CATEGORY_FIXES = "Fixes",
        CATEGORY_ROLE_PLAYING = "Role Playing",
        CATEGORY_WORLD_EDITING_AND_MANAGEMENT = "World Editing and Management",
        CATEGORY_TELEPORTATION = "Teleportation",
        CATEGORY_INFORMATIONAL = "Informational",
        CATEGORY_ECONOMY = "Economy",
        CATEGORY_CHAT_RELATED = "Chat Related",
        CATEGORY_MISCELLANEOUS = "Miscellaneous",
        CATEGORY_WORLD_GENERATORS = "World Generators",
        CATEGORY_WEBSITE_ADMINISTRATION = "Website Administration";

    public static List<BukkitPlugin> getPluginsByCategory(String category) throws Exception {
        String result = NetUtils.get("http://api.bukget.org/3//categories/" + category + "?fields=slug,plugin_name,description,versions.version,versions.game_versions");
        Gson gson = new Gson();
        List<BukkitPlugin> list = gson.fromJson(result, new TypeToken<List<BukkitPlugin>>() {
                                            }.getType());
        return list;
    }

    public static List<Category> getCategories() throws Exception {
        String result = NetUtils.get("http://api.bukget.org/3//categories/");
        Gson gson = new Gson();
        List<Category> list = gson.fromJson(result, new TypeToken<List<Category>>() {
                                        }.getType());
        return list;
    }

    public static PluginInfo getPluginInfo(String slug) throws Exception {
        if (StrUtils.isNotBlank(slug)) {
            String result = NetUtils.get("http://api.bukget.org/3//plugins/bukkit/" + slug.toLowerCase());
            if (StrUtils.isNotBlank(result))
                if (!result.equals("null")) {
                    PluginInfo info = new Gson().fromJson(result, PluginInfo.class);
                    return info;
                }
        }
        return null;
    }
}
