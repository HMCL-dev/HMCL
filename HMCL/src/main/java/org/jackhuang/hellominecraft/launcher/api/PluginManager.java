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
package org.jackhuang.hellominecraft.launcher.api;

import org.jackhuang.hellominecraft.utils.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.settings.DefaultPlugin;

/**
 *
 * @author huangyuhui
 */
public class PluginManager {

    public static IPlugin NOW_PLUGIN = new DefaultPlugin();

    public static void getServerPlugin() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class c = cl.loadClass("org.jackhuang.hellominecraft.launcher.servers.ServerPlugin");
            IPlugin p = (IPlugin) c.newInstance();
            NOW_PLUGIN = p;
        } catch (ClassNotFoundException ignore) {
        } catch (Exception e) {
            HMCLog.err("Failed  to new instance");
        }
    }

}
