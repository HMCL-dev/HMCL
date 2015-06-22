/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.settings;

import java.util.ArrayList;

/**
 *
 * @author hyh
 */
public class Settings {
    
    public boolean checkUpdate;
    public String maxMemory;
    public String mainjar, bgPath, javaDir, javaArgs;
    public ArrayList<String> inactiveExtMods, inactiveCoreMods, inactivePlugins,
            inactiveWorlds;
    public ArrayList<Schedule> schedules;
    
    public Settings() {
        maxMemory = "1024";
        checkUpdate = true;
        schedules = new ArrayList<Schedule>();
        mainjar = bgPath = javaDir = javaArgs = "";
        inactiveExtMods = new ArrayList<String>();
        inactiveCoreMods = new ArrayList<String>();
        inactivePlugins = new ArrayList<String>();
        inactiveWorlds = new ArrayList<String>();
    }
    
}
