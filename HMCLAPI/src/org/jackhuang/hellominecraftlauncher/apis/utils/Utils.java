/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.utils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工具类
 *
 * @author hyh
 */
public class Utils {
/*
    public static boolean deleteAll(File f) {
        if (f == null || !f.exists()) {
            return false;
        }
        if (f.isFile()) {
            return f.delete();
        } else {
            File f1[] = f.listFiles();
            int len = f1.length;
            boolean flag = true;
            for (int i = 0; i < len; i++) {
                flag &= deleteAll(f1[i]);
            }
            return flag && f.delete();
        }
    }

    // 复制文件
    public static void copyFile(File sourceFile, File targetFile) throws IOException {
        BufferedInputStream inBuff = null;
        BufferedOutputStream outBuff = null;
        new File(targetFile.getParent()).mkdirs();
        try {
            // 新建文件输入流并对它进行缓冲
            inBuff = new BufferedInputStream(new FileInputStream(sourceFile));

            // 新建文件输出流并对它进行缓冲
            outBuff = new BufferedOutputStream(new FileOutputStream(targetFile));

            // 缓冲数组
            byte[] b = new byte[1024 * 5];
            int len;
            while ((len = inBuff.read(b)) != -1) {
                outBuff.write(b, 0, len);
            }
            // 刷新此缓冲的输出流
            outBuff.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // 关闭流
            if (inBuff != null) {
                inBuff.close();
            }
            if (outBuff != null) {
                outBuff.close();
            }
        }

    }*/

    /*public static void copyFile(String sourceFile, String targetFile) throws IOException {
        copyFile(new File(sourceFile), new File(targetFile));
    }
    // 复制文件夹

    public static void copyDirectiory(File sourceDir, File targetDir) throws IOException {
        // 新建目标目录
        targetDir.mkdirs();
        // 获取源文件夹当前下的文件或目录
        File[] file = sourceDir.listFiles();
        if (file == null) {
            return;
        }
        for (int i = 0; i < file.length; i++) {
            if (file[i].isFile()) {
                // 源文件
                File sourceFile = file[i];
                // 目标文件
                File targetFile = new File(targetDir.getAbsolutePath() + File.separator + file[i].getName());
                copyFile(sourceFile, targetFile);
            }
            if (file[i].isDirectory()) {
                // 准备复制的源文件夹
                String dir1 = sourceDir + File.separator + file[i].getName();
                // 准备复制的目标文件夹
                String dir2 = targetDir + File.separator + file[i].getName();
                copyDirectiory(dir1, dir2);
            }
        }
    }

    // 复制文件夹
    public static void copyDirectiory(String sourceDir, String targetDir) throws IOException {
        copyDirectiory(new File(sourceDir), new File(targetDir));
    }*/

    /*public static String readToEnd(File f) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(f));
            return readToEnd(reader);
        } catch (IOException e) {
            //e.printStackTrace();
            return "";
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                HMCLLog.err("Utils.readToEnd", ex);
            }
        }
    }

    public static String readToEnd(InputStream f) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(f));
            return readToEnd(reader);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    public static String readToEnd(BufferedReader reader) {
        try {
            String res = "", line;
            while ((line = reader.readLine()) != null) {
                res += line;
            }
            return res;
        } catch (IOException e) {
            e.printStackTrace();
            return "";
        }
    }

    public static void writeToFile(File f, String content) {
        try {
            if (!f.exists()) {
                f.createNewFile();
            }
            BufferedWriter writer = new BufferedWriter(new FileWriter(f));
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeToFile(File f, String content, String encoding) {
        try {
            if (!f.exists()) {
                f.createNewFile();
            }
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), encoding));
            writer.write(content);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }*/

    public static String[] getURL() {
        URL[] urls = ((URLClassLoader) Utils.class.getClassLoader()).getURLs();
        String[] urlStrings = new String[urls.length];
        for (int i = 0; i < urlStrings.length; i++) {
            try {
                urlStrings[i] = URLDecoder.decode(urls[i].getPath(), "UTF-8");
            } catch (UnsupportedEncodingException ex) {
                Logger.getLogger(Utils.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return urlStrings;
    }

    public static void addDir(String s) throws IOException {
        try {
            Field field = ClassLoader.class.getDeclaredField("usr_paths");
            field.setAccessible(true);
            String[] paths = (String[]) field.get(null);
            for (int i = 0; i < paths.length; i++) {
                if (s.equals(paths[i])) {
                    return;
                }
            }
            String[] tmp = new String[paths.length + 1];
            System.arraycopy(paths, 0, tmp, 0, paths.length);
            tmp[paths.length] = s;
            field.set(null, tmp);
        } catch (IllegalAccessException e) {
            throw new IOException("Failed to get permissions to set library path");
        } catch (NoSuchFieldException e) {
            throw new IOException("Failed to get field handle to set library path");
        }
    }
}
