/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.utils;

import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;

/**
 *
 * @author hyh
 */
public class Utilities {

    public static String addSeparator(String path) {
        if (path == null || path.trim().length() == 0) {
            return "";
        }
        if (path.charAt(path.length() - 1) == File.separatorChar) {
            return path;
        } else {
            return path + File.separatorChar;
        }
    }

    public static boolean isSeparator(char ch) {
        return ch == File.separatorChar || ch == '/' || ch == '\\';
    }

    public static String removeLastSeparator(String dir) {
        String t = dir.trim();
        char ch = t.charAt(t.length() - 1);
        if (isSeparator(ch)) {
            return t.substring(0, t.length() - 1);
        }
        return t;
    }

    public static String extractLastDirectory(String dir) {
        String t = removeLastSeparator(dir);
        int i = t.length() - 1;
        while (i >= 0 && !isSeparator(dir.charAt(i))) {
            i--;
        }
        if (i < 0) {
            return t;
        }
        return t.substring(i + 1, (t.length() - i) + (i + 1) - 1);
    }

    public static ArrayList<String> findAllFile(File f) {
        ArrayList<String> arr = new ArrayList<String>();
        if (!f.exists()) {
            return arr;
        }
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++) {
                if (f1[i].isFile()) {
                    arr.add(f1[i].getName());
                }
            }
        }
        return arr;
    }

    public static ArrayList<String> findAllDir(File f) {
        ArrayList<String> arr = new ArrayList<String>();
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++) {
                if (f1[i].isDirectory()) {
                    arr.add(f1[i].getName());
                }
            }
        }
        return arr;
    }

    public static void deleteAll(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isFile()) {
            f.delete();
        } else {
            File f1[] = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++) {
                deleteAll(f1[i]);
            }
            f.delete();
        }
    }

    public static String extractFileName(String fileName) {
        File file = new File(fileName);
        return file.getName();
    }

    public static boolean is16Folder(String path) {
        path = Utilities.addSeparator(path);
        if (new File(path + "versions").exists()) {
            return true;
        }
        return false;
    }

    public static boolean isEmpty(String s) {
        return s == null || s.trim().equals("");
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }
    public static int tryParseInteger(String integer, int def) {
        try {
            return Integer.parseInt(integer);
        } catch (NumberFormatException localNumberFormatException) {
            return def;
        }
    }

    public static boolean isEquals(String base, String to) {
        if (base == null) {
            return (to == null);
        } else {
            return base.equals(to);
        }
    }

    public static String getGameDir() {
        String path = new File(SettingsManager.settings.mainjar).getParent();
        path = Utilities.addSeparator(path);
        return path;
    }

    public static String getPath(String lastFolder) {
        String path = getGameDir();
        File file = new File((new StringBuilder()).append(path).append(lastFolder).toString());
        if (file.exists()) {
            return file.getPath();
        } else {
            return null;
        }
    }

    public static String try2GetPath(String lastFolder) {
        String path = getGameDir();
        return (new StringBuilder()).append(path).append(lastFolder).toString();

    }

    public static String trimExtension(String filename) {
        if ((filename != null) && (filename.length() > 0)) {
            int i = filename.lastIndexOf('.');
            if ((i > -1) && (i < (filename.length()))) {
                return filename.substring(0, i);
            }
        }
        return filename;
    }

    public static boolean openLink(String url) {
        boolean isBrowsed = false;
        //判断当前系统是否支持Java AWT Desktop扩展
        if (java.awt.Desktop.isDesktopSupported()) {
            try {
//创建一个URI实例
                java.net.URI uri = java.net.URI.create(url);
//获取当前系统桌面扩展
                java.awt.Desktop dp = java.awt.Desktop.getDesktop();
//判断系统桌面是否支持要执行的功能
                if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) {
//获取系统默认浏览器打开链接
                    dp.browse(uri);
                    isBrowsed = true;
                }
            } catch (java.lang.NullPointerException e) {
//此为uri为空时抛出异常
            } catch (java.io.IOException e) {
//此为无法获取系统默认浏览器
            }
        }
        return isBrowsed;
    }
}
