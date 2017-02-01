/*
 * Hello Minecraft!.
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
package org.jackhuang.hellominecraft.util;

import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;

/**
 * @author huangyuhui
 */
public final class Utils {

    private Utils() {
    }

    public static URL[] getURL() {
        return ((URLClassLoader) Utils.class.getClassLoader()).getURLs();
    }

    public static void setClipborad(String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        } catch(HeadlessException ignored) {
        }
    }

    /**
     * In order to fight against the permission manager by Minecraft Forge.
     *
     * @param status exit code
     */
    public static void shutdownForcely(int status) throws Exception {
        AccessController.doPrivileged((PrivilegedExceptionAction<Void>) () -> {
            Class<?> z = Class.forName("java.lang.Shutdown");
            Method exit = z.getDeclaredMethod("exit", int.class);
            exit.setAccessible(true);
            exit.invoke(z, status);
            return null;
        });
    }
}
