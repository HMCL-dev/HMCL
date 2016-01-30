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

import java.util.List;

/**
 *
 * @author huangyuhui
 */
public class BukkitPlugin {

    public String description, plugin_name, slug;
    public List<PluginVersion> versions;

    public String getLatestVersion() {
        if (versions != null) {
            PluginVersion v = versions.get(0);
            return v.version;
        }
        return null;
    }

    public String getLatestBukkit() {
        if (versions != null) {
            PluginVersion v = versions.get(0);
            List<String> al = v.game_versions;
            if (al != null && !al.isEmpty())
                return al.get(0);
        }
        return "";
    }

}
