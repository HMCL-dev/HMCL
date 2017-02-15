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
package org.jackhuang.hmcl.api;

import org.jackhuang.hmcl.api.ui.AddTabCallback;
import java.util.ArrayList;
import javax.swing.JFrame;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.func.Consumer;

/**
 * Can be only called by HMCL.
 *
 * @author huangyuhui
 */
public class PluginManager {

    private static final ArrayList<IPlugin> PLUGINS = new ArrayList<>();

    public static void getPlugin(Class<?> cls) {
        try {
            IPlugin p = (IPlugin) cls.newInstance();
            PLUGINS.add(p);
        } catch (Throwable e) {
            System.err.println("Failed to new instance");
            e.printStackTrace();
        }
    }

    public static void fireRegisterAuthenticators(Consumer<IAuthenticator> callback) {
        for (IPlugin p : PLUGINS)
            p.onRegisterAuthenticators(callback);
    }

    public static void fireAddTab(JFrame frame, AddTabCallback callback) {
        for (IPlugin p : PLUGINS)
            p.onAddTab(frame, callback);
    }

}
