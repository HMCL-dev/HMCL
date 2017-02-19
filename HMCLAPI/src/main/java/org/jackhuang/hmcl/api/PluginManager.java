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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import org.jackhuang.hmcl.api.ui.AddTabCallback;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.swing.JFrame;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.func.Consumer;

/**
 * Can be only called by HMCL.
 *
 * @author huangyuhui
 */
public class PluginManager {

    private static final File PLUGINS_FILE = new File("plugins").getAbsoluteFile();
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

    public static void loadPlugins() {
        ArrayList<URL> urls = new ArrayList<>();
        ArrayList<JarFile> jars = new ArrayList<>();
        if (PLUGINS_FILE.isDirectory()) {
            for (File f : PLUGINS_FILE.listFiles(f -> f.isFile() && f.getName().endsWith(".jar")))
                try {
                    jars.add(new JarFile(f));
                    urls.add(f.toURI().toURL());
                } catch (IOException e) {
                    System.err.println("A malformed jar file: " + f);
                    e.printStackTrace();
                }

            URLClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
            for (JarFile f : jars)
                for (Enumeration<JarEntry> entries = f.entries(); entries.hasMoreElements();)
                    try {
                        JarEntry entry = entries.nextElement();
                        String clsName = entry.getName();
                        if (clsName.endsWith(".class") && !clsName.contains("$")) {
                            clsName = clsName.replace('/', '.').replace(".class", "");
                            Class clazz = classLoader.loadClass(clsName);
                            if (IPlugin.class.isAssignableFrom(clazz))
                                getPlugin(clazz);
                        }
                    } catch (ClassNotFoundException ex) {
                        ex.printStackTrace();
                    }
        }
    }

}
