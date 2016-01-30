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
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.Random;
import javax.swing.ImageIcon;

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

    public static String[] getURL() {
        URL[] urls = ((URLClassLoader) Utils.class.getClassLoader()).getURLs();
        String[] urlStrings = new String[urls.length];
        for (int i = 0; i < urlStrings.length; i++)
            try {
                urlStrings[i] = URLDecoder.decode(urls[i].getPath(), "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                HMCLog.warn("Unsupported UTF-8 encoding", ex);
            }
        return urlStrings;
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

    public static ImageIcon scaleImage(ImageIcon i, int x, int y) {
        return new ImageIcon(i.getImage().getScaledInstance(x, y, Image.SCALE_SMOOTH));
    }

    public static ImageIcon searchBackgroundImage(ImageIcon init, String bgpath, int width, int height) {
        Random r = new Random();
        boolean loaded = false;
        ImageIcon background = init;

        // bgpath
        if (StrUtils.isNotBlank(bgpath) && !loaded) {
            String[] backgroundPath = bgpath.split(";");
            if (backgroundPath.length > 0) {
                int index = r.nextInt(backgroundPath.length);
                background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundPath[index]).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in bgpath.");
                loaded = true;
            }
        }

        // bgskin
        if (!loaded) {
            File backgroundImageFile = new File("bg");
            if (backgroundImageFile.exists() && backgroundImageFile.isDirectory()) {
                String[] backgroundPath = backgroundImageFile.list();
                if (backgroundPath.length > 0) {
                    int index = r.nextInt(backgroundPath.length);
                    background = new ImageIcon(Toolkit.getDefaultToolkit().getImage("bg" + File.separator + backgroundPath[index]).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                    HMCLog.log("Prepared background image in bgskin folder.");
                    loaded = true;
                }
            }
        }

        // background.png
        if (!loaded) {
            File backgroundImageFile = new File("background.png");
            if (backgroundImageFile.exists()) {
                loaded = true;
                background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundImageFile.getAbsolutePath()).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in background.png.");
            }
        }

        // background.jpg
        if (!loaded) {
            File backgroundImageFile = new File("background.jpg");
            if (backgroundImageFile.exists()) {
                //loaded = true;
                background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundImageFile.getAbsolutePath()).getScaledInstance(width, height, Image.SCALE_DEFAULT));
                HMCLog.log("Prepared background image in background.jpg.");
            }
        }

        if (background == null)
            return init;
        return background;
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
}
