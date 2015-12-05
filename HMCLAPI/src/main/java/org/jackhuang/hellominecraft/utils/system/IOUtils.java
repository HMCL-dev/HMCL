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
package org.jackhuang.hellominecraft.utils.system;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huang
 */
public class IOUtils {

    public static String addSeparator(String path) {
        if (path == null || path.trim().length() == 0)
            return "";
        if (isSeparator(path.charAt(path.length() - 1)))
            return path;
        else
            return path + File.separatorChar;
    }

    public static boolean isSeparator(char ch) {
        return ch == File.separatorChar || ch == '/' || ch == '\\';
    }

    public static String removeLastSeparator(String dir) {
        String t = dir.trim();
        char ch = t.charAt(t.length() - 1);
        if (isSeparator(ch))
            return t.substring(0, t.length() - 1);
        return t;
    }

    public static String extractLastDirectory(String dir) {
        String t = removeLastSeparator(dir);
        int i = t.length() - 1;
        while (i >= 0 && !isSeparator(dir.charAt(i)))
            i--;
        if (i < 0)
            return t;
        return t.substring(i + 1, (t.length() - i) + (i + 1) - 1);
    }

    public static ArrayList<String> findAllFile(File f) {
        ArrayList<String> arr = new ArrayList<>();
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++)
                if (f1[i].isFile())
                    arr.add(f1[i].getName());
        }
        return arr;
    }

    public static ArrayList<String> findAllFileWithFullName(File f) {
        ArrayList<String> arr = new ArrayList<>();
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++)
                if (f1[i].isFile())
                    arr.add(addSeparator(f.getAbsolutePath()) + f1[i].getName());
        }
        return arr;
    }

    public static ArrayList<String> findAllDir(File f) {
        ArrayList<String> arr = new ArrayList<>();
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++)
                if (f1[i].isDirectory())
                    arr.add(f1[i].getName());
        }
        return arr;
    }

    public static File currentDir() {
        return new File(".");
    }

    public static String currentDirWithSeparator() {
        return addSeparator(currentDir().getAbsolutePath());
    }

    public static String getLocalMAC() {
        InetAddress addr;
        try {
            addr = InetAddress.getLocalHost();
            String ip = addr.getHostAddress();
            return getMacAddress(ip);
        } catch (UnknownHostException e) {
            HMCLog.warn("Failed to get local mac address because of the unknown host.", e);
        }
        return "ERROR";
    }

    public static String getMacAddress(String host) {
        String mac;
        StringBuilder sb = new StringBuilder();

        try {
            NetworkInterface ni = NetworkInterface.getByInetAddress(InetAddress.getByName(host));

            byte[] macs = ni.getHardwareAddress();

            for (int i = 0; i < macs.length; i++) {
                mac = Integer.toHexString(macs[i] & 0xFF);

                if (mac.length() == 1)
                    mac = '0' + mac;

                sb.append(mac).append("-");
            }

        } catch (SocketException e) {
            HMCLog.warn("Failed to get mac address because the socket has thrown an exception.", e);
        } catch (UnknownHostException e) {
            HMCLog.warn("Failed to get mac address because of the unknown host.", e);
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
        return getJavaDir(System.getProperty("java.home"));
    }

    public static String getJavaDir(String home) {
        String path = home + File.separatorChar + "bin" + File.separatorChar;
        path = addSeparator(path);
        if (OS.os() == OS.WINDOWS && new File(path + "javaw.exe").isFile())
            return path + "javaw.exe";
        else
            return path + "java";
    }

    public static byte[] readFully(InputStream stream) throws IOException {
        byte[] data = new byte[4096];
        ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
        int len;
        do {
            len = stream.read(data);
            if (len <= 0)
                continue;
            entryBuffer.write(data, 0, len);
        } while (len != -1);

        return entryBuffer.toByteArray();
    }

    public static void closeQuietly(Reader input) {
        closeQuietly((Closeable) input);
    }

    public static void closeQuietly(Writer output) {
        closeQuietly((Closeable) output);
    }

    public static void closeQuietly(InputStream input) {
        closeQuietly((Closeable) input);
    }

    public static void closeQuietly(OutputStream output) {
        closeQuietly((Closeable) output);
    }

    public static void closeQuietly(Closeable closeable) {
        try {
            if (closeable != null)
                closeable.close();
        } catch (IOException ioe) {
        }
    }

    public static void write(byte[] data, OutputStream output)
    throws IOException {
        if (data != null)
            output.write(data);
    }

    public static void write(String data, OutputStream output, String encoding)
    throws IOException {
        if (data != null)
            output.write(data.getBytes(encoding));
    }

    public static FileInputStream openInputStream(File file)
    throws IOException {
        if (file.exists()) {
            if (file.isDirectory())
                throw new IOException("File '" + file + "' exists but is a directory");
            if (!file.canRead())
                throw new IOException("File '" + file + "' cannot be read");
        } else
            throw new FileNotFoundException("File '" + file + "' does not exist");
        return new FileInputStream(file);
    }

    public static String tryGetCanonicalFolderPath(File file) {
        try {
            return IOUtils.addSeparator(file.getCanonicalPath());
        } catch (IOException ignored) {
            return IOUtils.addSeparator(file.getAbsolutePath());
        }
    }

    public static File tryGetCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException ignored) {
            return file.getAbsoluteFile();
        }
    }

    public static File tryGetCanonicalFile(String file) {
        return tryGetCanonicalFile(new File(file));
    }

    public static String tryGetCanonicalFilePath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    public static URL parseURL(String str) {
        try {
            return new URL(str);
        } catch (MalformedURLException ex) {
            HMCLog.warn("Failed to parse URL:" + str);
            return null;
        }
    }

    public static List<String> readProcessByInputStream(String[] cmd) throws IOException, InterruptedException {
        JavaProcess jp = new JavaProcess(cmd, new ProcessBuilder(cmd).start(), null);
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(jp.getRawProcess().getInputStream()))) {
            jp.getRawProcess().waitFor();
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line);
        }
        return lines;
    }

    public static List<String> readProcessByErrorStream(String[] cmd) throws IOException, InterruptedException {
        JavaProcess jp = new JavaProcess(cmd, new ProcessBuilder(cmd).start(), null);
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(jp.getRawProcess().getErrorStream()))) {
            jp.getRawProcess().waitFor();
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line);
        }
        return lines;
    }
}
