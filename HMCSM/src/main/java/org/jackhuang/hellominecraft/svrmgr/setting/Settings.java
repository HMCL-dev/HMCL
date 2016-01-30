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

import java.util.ArrayList;

/**
 *
 * @author huangyuhui
 */
public class Settings {

    public boolean checkUpdate;
    public String maxMemory;
    public String mainjar, bgPath, javaDir, javaArgs;
    public ArrayList<String> inactiveExtMods, inactiveCoreMods, inactivePlugins,
        inactiveWorlds;
    public ArrayList<Schedule> schedules;

    public Settings() {
        maxMemory = "1024";
        checkUpdate = true;
        schedules = new ArrayList<>();
        mainjar = bgPath = javaDir = javaArgs = "";
        inactiveExtMods = new ArrayList<>();
        inactiveCoreMods = new ArrayList<>();
        inactivePlugins = new ArrayList<>();
        inactiveWorlds = new ArrayList<>();
    }

}
