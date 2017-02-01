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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class Java {

    public static final List<Java> JAVA;

    static {
        List<Java> temp = new ArrayList<>();
        temp.add(new Java("Default", System.getProperty("java.home")));
        temp.add(new Java("Custom", null));
        if (OS.os() == OS.WINDOWS)
            temp.addAll(Java.queryAllJavaHomeInWindowsByReg().values());
        else if (OS.os() == OS.OSX)
            temp.addAll(Java.queryAllJDKInMac());
        JAVA = Collections.unmodifiableList(temp);
    }

    public static Java suggestedJava() {
        for (Java j : JAVA)
            if (j.name.startsWith("1.8") || j.name.startsWith("9"))
                return j;
        return JAVA.get(0);
    }

    String name, home;

    public Java(String name, String home) {
        this.name = name;
        this.home = home;
    }

    public String getName() {
        return name;
    }

    public String getLocalizedName() {
        if (name.equals("Default"))
            return C.i18n("settings.default");
        if (name.equals("Custom"))
            return C.i18n("settings.custom");
        return name;
    }

    public String getHome() {
        return home;
    }

    public String getJava() {
        return IOUtils.getJavaDir(getHome());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Java) {
            Java j = (Java) obj;
            return (j.getName() == null && this.getName() == null) || ((Java) obj).getName().equals(this.getName());
        } else
            return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /*
     * -----------------------------------
     * MAC OS X
     * -----------------------------------
     */
    public static List<Java> queryAllJDKInMac() {
        List<Java> ans = new ArrayList<>();
        File jre = new File("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home");
        if (jre.exists())
            ans.add(new Java("JRE", jre.getPath()));
        File f = new File("/Library/Java/JavaVirtualMachines/");
        if (f.exists())
            for (File a : f.listFiles())
                ans.add(new Java(a.getName(), new File(a, "Contents/Home").getAbsolutePath()));
        return ans;
    }

    /*
     * -----------------------------------
     * WINDOWS
     * -----------------------------------
     */
    public static Map<String, Java> queryAllJavaHomeInWindowsByReg() {
        Map<String, Java> ans = new HashMap<>();
        try {
            queryJava(ans, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\");
            queryJava(ans, "HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\");
        } catch (IOException | InterruptedException ex) {
            HMCLog.err("Faield to query java", ex);
        }
        return ans;
    }

    private static void queryJava(Map<String, Java> ans, String location) throws IOException, InterruptedException {
        for (String java : queryRegSubFolders(location)) {
            int s = 0;
            for (char c : java.toCharArray())
                if (c == '.')
                    ++s;
            if (s <= 1)
                continue;
            String javahome = queryRegValue(java, "JavaHome"), ver = java.substring(location.length());
            if (javahome != null && !ans.containsKey(ver))
                ans.put(ver, new Java(ver, javahome));
        }
    }

    private static List<String> queryRegSubFolders(String location) throws IOException, InterruptedException {
        String[] cmd = new String[] { "cmd", "/c", "reg", "query", location };
        List<String> l = IOUtils.readProcessByInputStream(cmd);
        List<String> ans = new ArrayList<>();
        for (String line : l)
            if (line.startsWith(location) && !line.equals(location))
                ans.add(line);
        return ans;
    }

    private static String queryRegValue(String location, String name) throws IOException, InterruptedException {
        String[] cmd = new String[] { "cmd", "/c", "reg", "query", location, "/v", name };
        List<String> l = IOUtils.readProcessByInputStream(cmd);
        boolean last = false;
        for (String s : l) {
            if (s.trim().isEmpty())
                continue;
            if (last == true && s.trim().startsWith(name)) {
                int begins = s.indexOf(name);
                if (begins > 0) {
                    s = s.substring(begins + name.length());
                    begins = s.indexOf("REG_SZ");
                    if (begins > 0) {
                        s = s.substring(begins + "REG_SZ".length());
                        return s.trim();
                    }
                }
            }
            if (s.trim().equals(location))
                last = true;
        }
        return null;
    }

}
