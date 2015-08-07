/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils.system;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class Java {

    String name, home;

    public Java(String name, String home) {
        this.name = name;
        this.home = home;
    }

    public String getName() {
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
        } else return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    public static List<Java> queryAllJavaHomeInWindowsByReg() {
        try {
            List<Java> ans = new ArrayList<>();
            List<String> javas = queryRegSubFolders("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment");
            for (String java : javas) {
                int s = 0;
                for (char c : java.toCharArray())
                    if (c == '.') s++;
                if (s <= 1) continue;
                String javahome = queryRegValue(java, "JavaHome");
                if (javahome != null)
                    ans.add(new Java(java.substring("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\".length()), javahome));
            }
            return ans;
        } catch (IOException | InterruptedException ex) {
            HMCLog.err("Faield to query java", ex);
            return null;
        }
    }

    private static List<String> queryRegSubFolders(String location) throws IOException, InterruptedException {
        String[] cmd = new String[]{"cmd", "/c", "reg", "query", location};
        List<String> l = IOUtils.readProcessByInputStream(cmd);
        List<String> ans = new ArrayList<>();
        for (String line : l) {
            if (line.startsWith(location) && !line.equals(location))
                ans.add(line);
        }
        return ans;
    }

    private static String queryRegValue(String location, String name) throws IOException, InterruptedException {
        String[] cmd = new String[]{"cmd", "/c", "reg", "query", location, "/v", name};
        List<String> l = IOUtils.readProcessByInputStream(cmd);
        boolean last = false;
        for(String s : l) {
            if(s.trim().isEmpty()) continue;
            if (last == true && s.trim().startsWith(name)) {
                int begins = s.indexOf(name);
                if(begins > 0) {
                    s = s.substring(begins + name.length());
                    begins = s.indexOf("REG_SZ");
                    if(begins > 0) {
                        s = s.substring(begins + "REG_SZ".length());
                        return s.trim();
                    }
                }
            }
            if(s.trim().equals(location)) last = true;
        }
        return null;
    }

}
