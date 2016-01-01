/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

import java.io.File;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;

/**
 *
 * @author huang
 */
public class IOUtils {

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

    public static ArrayList<String> findAllFileWithFullName(File f) {
        ArrayList<String> arr = new ArrayList<String>();
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++) {
                if (f1[i].isFile()) {
                    arr.add(addSeparator(f.getAbsolutePath()) + f1[i].getName());
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

    public static String currentDir() {
        /*File file = new File(".");
         try {
         return file.getCanonicalPath();
         } catch (IOException e) {
         e.printStackTrace();
         return "";
         }*/
        return System.getProperty("user.dir");
    }
    
    public static String currentDirWithSeparator() {
        return addSeparator(currentDir());
    }

    public static String getLocalMAC() {
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress().toString();//获得本机IP  
            return getMacAddress(ip);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return "ERROR";
    }

    public static String getMacAddress(String host) {
        String mac = "";
        StringBuffer sb = new StringBuffer();

        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(host));

            byte[] macs = ni.getHardwareAddress();

            for (int i = 0; i < macs.length; i++) {
                mac = Integer.toHexString(macs[i] & 0xFF);

                if (mac.length() == 1) {
                    mac = '0' + mac;
                }

                sb.append(mac + "-");
            }

        } catch (SocketException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        mac = sb.toString();
        mac = mac.substring(0, mac.length() - 1);

        return mac;
    }

    public static String extractFileName(String fileName) {
        File file = new File(fileName);
        return file.getName();
    }

    public static String getJavaDir() {
        return getJavaDir(false);
    }

    public static String getJavaDir(boolean debugMode) {
        String separator = System.getProperty("file.separator");
        String path = (new StringBuilder()).append(System.getProperty("java.home")).append(separator).append("bin").append(separator).toString();
        if (OS.os() == OS.WINDOWS && (new File((new StringBuilder()).append(path).append("javaw.exe").toString())).isFile() && !debugMode) {
            return (new StringBuilder()).append(path).append("javaw.exe").toString();
        } else {
            return (new StringBuilder()).append(path).append("java").toString();
        }
    }
    
}
