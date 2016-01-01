/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

/**
 * 操作系统
 * @author hyh
 */
public enum OS {
    
    LINUX,
    WINDOWS,
    OSX,
    UNKOWN;

    public static OS os() {
        String str;
        if ((str = System.getProperty("os.name").toLowerCase())
                .contains("win")) {
            return OS.WINDOWS;
        }
        if (str.contains("mac")) {
            return OS.OSX;
        }
        if (str.contains("solaris")) {
            return OS.LINUX;
        }
        if (str.contains("sunos")) {
            return OS.LINUX;
        }
        if (str.contains("linux")) {
            return OS.LINUX;
        }
        if (str.contains("unix")) {
            return OS.LINUX;
        }
        return OS.UNKOWN;
    }
    
}
