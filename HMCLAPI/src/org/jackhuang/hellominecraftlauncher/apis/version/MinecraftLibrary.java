/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.apis.version;

import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.jackhuang.hellominecraftlauncher.apis.utils.OS;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;

/**
 *
 * @author hyh
 */
public class MinecraftLibrary {

    public ArrayList<Rules> rules;
    public String name, url, formatted;
    public Natives natives;

    /**
     * 此library是否被允许使用
     *
     * @return
     */
    public boolean allow() {
        boolean flag = false;
        if (rules == null || rules.isEmpty()) {
            flag = true;
        } else {
            for (int j = 0; j < rules.size(); j++) {
                Rules r = rules.get(j);
                if (r.action.equals("disallow")) {
                    if (r.os != null && (StringUtils.isBlank(r.os.name) || r.os.name.toUpperCase().equals(OS.os().toString()))) {
                        flag = false;
                        break;
                    }
                } else {
                    if (r.os != null && (StringUtils.isBlank(r.os.name) || r.os.name.toUpperCase().equals(OS.os().toString()))) {
                        flag = true;
                    }
                    if (r.os == null) {
                        flag = true;
                    }
                }
            }
        }
        return flag;
    }

    private String format(String nati) {
        String arch = System.getProperty("os.arch");
        if (arch.contains("64")) {
            arch = "64";
        } else {
            arch = "32";
        }
        if(nati == null) return "";
        return nati.replace("${arch}", arch);
    }

    public String getNative() {
        OS os = OS.os();
        if (os == OS.WINDOWS) {
            return format(natives.windows);
        } else if (os == OS.OSX) {
            return format(natives.osx);
        } else {
            return format(natives.linux);
        }
    }
}
