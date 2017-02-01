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
package org.jackhuang.hellominecraft.util.sys;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huang
 */
public final class IOUtils {

    private IOUtils() {
    }

    public static String addSeparator(String path) {
        if (path == null || path.trim().length() == 0)
            return "";
        if (isSeparator(path.charAt(path.length() - 1)))
            return path;
        else
            return path + File.separatorChar;
    }

    public static String addURLSeparator(String path) {
        if (path == null || path.trim().length() == 0)
            return "";
        if (path.charAt(path.length() - 1) == '/')
            return path;
        else
            return path + '/';
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

    public static void findAllFile(File f, Consumer<String> callback) {
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++)
                if (f1[i].isFile())
                    callback.accept(f1[i].getName());
        }
    }

    public static void findAllDir(File f, Consumer<String> callback) {
        if (f.isDirectory()) {
            File[] f1 = f.listFiles();
            int len = f1.length;
            for (int i = 0; i < len; i++)
                if (f1[i].isDirectory())
                    callback.accept(f1[i].getName());
        }
    }
    
    public static String getRealPath() {
        String realPath = IOUtils.class.getClassLoader().getResource("").getFile();
        java.io.File file = new java.io.File(realPath);
        realPath = file.getAbsolutePath();
        try {
            realPath = java.net.URLDecoder.decode(realPath, DEFAULT_CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return realPath;
    }

    public static boolean isAbsolutePath(String path) {
        if (path == null)
            return true;
        return path.startsWith("/") || path.indexOf(':') > 0;
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
    
    public static List<String> readLines(InputStream stream, String charset) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(stream, charset));
        ArrayList<String> ret = new ArrayList<>();
        for (String line; (line = br.readLine()) != null; ret.add(line));
        return ret;
    }

    public static ByteArrayOutputStream readFully(InputStream stream) throws IOException {
        byte[] data = new byte[MAX_BUFFER_SIZE];
        ByteArrayOutputStream entryBuffer = new ByteArrayOutputStream();
        int len;
        do {
            len = stream.read(data);
            if (len <= 0)
                continue;
            entryBuffer.write(data, 0, len);
        } while (len != -1);

        return entryBuffer;
    }
    
    public static byte[] toByteArray(InputStream stream) throws IOException {
        return readFully(stream).toByteArray();
    }
    
    public static String toString(InputStream is) throws IOException {
        return readFully(is).toString();
    }
    
    public static String toString(InputStream is, String charset) throws IOException {
        return readFully(is).toString(charset);
    }
    
    public static String toString(InputStream is, Charset charset) throws IOException {
        return readFully(is).toString(charset.name());
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
        return readProcessImpl(cmd, p -> p.getInputStream());
    }

    public static List<String> readProcessByErrorStream(String[] cmd) throws IOException, InterruptedException {
        return readProcessImpl(cmd, p -> p.getErrorStream());
    }
    
    private static List<String> readProcessImpl(String[] cmd, Function<Process, InputStream> callback) throws IOException, InterruptedException {
        JavaProcess jp = new JavaProcess(cmd, new ProcessBuilder(cmd).start(), null);
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(callback.apply(jp.getRawProcess()), Charset.defaultCharset()))) {
            jp.getRawProcess().waitFor();
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line);
        }
        return lines;
    }

    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        copyStream(input, output, new byte[MAX_BUFFER_SIZE]);
    }

    public static void copyStream(InputStream input, OutputStream output, byte[] buf) throws IOException {
        int length;
        while ((length = input.read(buf)) != -1)
            output.write(buf, 0, length);
    }

    /**
     * Max buffer size downloading.
     */
    public static final int MAX_BUFFER_SIZE = 4096;
    
    public static final String DEFAULT_CHARSET = "UTF-8";

    public static PrintStream createPrintStream(OutputStream out, Charset charset) {
        try {
            return new PrintStream(out, false, charset.name());
        } catch (UnsupportedEncodingException ignore) {
            return null;
        }
    }
}
