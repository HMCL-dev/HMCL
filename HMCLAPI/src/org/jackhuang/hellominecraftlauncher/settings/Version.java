/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.settings;

import java.io.File;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import org.jackhuang.hellominecraftlauncher.apis.C;
import org.jackhuang.hellominecraftlauncher.apis.utils.IOUtils;
import org.jackhuang.hellominecraftlauncher.apis.utils.MCUtils;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;

/**
 *
 * @author hyh
 */
public class Version {
    
    public boolean isVer16;
    public String name;
    public String gameDir, gameAssets, gameLibraries;
    public String mainClass, javaArgs, javaDir, minecraftArguments;
    public String maxMemory, maxPermGen;
    public String width, height;
    public boolean fullscreen;
    public ArrayList<String> firstLoadLibraries;
    public ArrayList<String> lastLoadLibraries;
    public ArrayList<String> minecraftJar;
    public ArrayList<Boolean> firstLoadLibrariesIsActive;
    public ArrayList<Boolean> lastLoadLibrariesIsActive;
    public ArrayList<Boolean> minecraftJarIsActive;
    public ArrayList<String> inactiveExtMods, inactiveCoreMods;
    public String userProperties;
    
    public Version()
    {
        isVer16 = true;
        gameDir = "";
        maxMemory = maxPermGen = width = height = name = "";
        fullscreen = false;
        mainClass = javaDir = javaArgs = gameAssets = gameLibraries = minecraftArguments = "";
        firstLoadLibraries = new ArrayList<String>();
        lastLoadLibraries = new ArrayList<String>();
        minecraftJar = new ArrayList<String>();
        firstLoadLibrariesIsActive = new ArrayList<Boolean>();
        lastLoadLibrariesIsActive = new ArrayList<Boolean>();
        minecraftJarIsActive = new ArrayList<Boolean>();
        inactiveExtMods = new ArrayList<String>();
        inactiveCoreMods = new ArrayList<String>();
        userProperties = "";
    }
    
    public Version(Version v)
    {
        this();
        if(v == null) return;
        isVer16 = v.isVer16;
        name = v.name;
        gameDir = v.gameDir;
        maxMemory = v.maxMemory;
        width = v.width;
        height = v.height;
        fullscreen = v.fullscreen;
        mainClass = v.mainClass;
        javaArgs = v.javaArgs;
        javaDir = v.javaDir;
        gameAssets = v.gameAssets;
        gameLibraries = v.gameLibraries;
        minecraftArguments = v.minecraftArguments;
        if(v.firstLoadLibraries != null)
            firstLoadLibraries = (ArrayList<String>)v.firstLoadLibraries.clone();
        if(v.lastLoadLibraries != null)
            lastLoadLibraries = (ArrayList<String>)v.lastLoadLibraries.clone();
        if(v.firstLoadLibrariesIsActive != null)
            firstLoadLibrariesIsActive = (ArrayList<Boolean>)v.firstLoadLibrariesIsActive.clone();
        if(v.lastLoadLibrariesIsActive != null)
            lastLoadLibrariesIsActive = (ArrayList<Boolean>)v.lastLoadLibrariesIsActive.clone();
        if(v.inactiveExtMods != null)
            inactiveExtMods = (ArrayList<String>)v.inactiveExtMods.clone();
        if(v.inactiveCoreMods != null)
            inactiveCoreMods = (ArrayList<String>)v.inactiveCoreMods.clone();
    }
    
    public String getMinecraftJar() {
        String minecraftJar;
        if (isVer16) {
            minecraftJar = IOUtils.addSeparator(gameDir)
                    + "versions" + File.separator + name + File.separator
                    + name + ".jar";
        } else {
            minecraftJar = IOUtils.addSeparator(gameDir) + "bin"
                    + File.separator + "minecraft.jar";
        }
        return minecraftJar;
    }
    
    public boolean exists(String defaultDir) {
        String gd = StringUtils.isBlank(gameDir) ? defaultDir : gameDir;
        if(isVer16) {
            MinecraftVersion v = MCUtils.getMinecraftVersion(this, defaultDir);
            if(v == null) return false;
            return new File(gd, C.FILE_MINECRAFT_VERSIONS + File.separator + v.id).exists();
        } else {
            return new File(gd, "bin").exists();
        }
    }
}
