/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import org.jackhuang.hellominecraft.HMCLog;

/**
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
    
    public static boolean is64Bit() {
	String arch = System.getProperty("os.arch");
	return arch.contains("64");
    }
    
    /**
     * @return Free Physical Memory Size (Byte)
     */
    public static long getTotalPhysicalMemory() {
        try {
            OperatingSystemMXBean o = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return o.getTotalPhysicalMemorySize();
        } catch(Throwable t) {
            HMCLog.warn("Failed to get total physical memory size", t);
            return -1;
        }
    }
    
}
