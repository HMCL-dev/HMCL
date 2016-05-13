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

import org.jackhuang.hellominecraft.util.logging.HMCLog;
import com.sun.management.OperatingSystemMXBean;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author huangyuhui
 */
public final class Utils {

    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static boolean isURL(String s) {
        try {
            new URL(s);
            return true;
        } catch (MalformedURLException ex) {
            return false;
        }
    }

    public static URL[] getURL() {
        return ((URLClassLoader) Utils.class.getClassLoader()).getURLs();
    }

    public static int getSuggestedMemorySize() {
        try {
            OperatingSystemMXBean osmb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            int memory = (int) (osmb.getTotalPhysicalMemorySize() / 1024 / 1024) / 4;
            memory = Math.round((float) memory / 128.0f) * 128;
            return memory;
        } catch (Throwable t) {
            HMCLog.warn("Failed to get total memory size, use 1024MB.", t);
            return 1024;
        }
    }

    public static void setClipborad(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /**
     * In order to fight against the permission manager by Minecraft Forge.
     *
     * @param status exit code
     */
    public static void shutdownForcely(int status) throws Exception {
        Class z = Class.forName("java.lang.Shutdown");
        Method exit = z.getDeclaredMethod("exit", int.class);
        exit.setAccessible(true);
        exit.invoke(z, status);
    }

    public static void requireNonNull(Object o) {
        if (o == null)
            throw new NullPointerException("Oh dear, there is a problem...");
    }

    public static Object firstNonNull(Object... o) {
        for (Object s : o)
            if (s != null)
                return s;
        return null;
    }
}
