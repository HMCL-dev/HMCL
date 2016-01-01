/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.utils.OperatingSystems;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;

/**
 *
 * @author hyh
 */
public abstract class IMinecraftLoader {
    protected String minecraftJar;
    protected Version v;
    protected LoginResult lr;

    public IMinecraftLoader(Version pub, Version ver, LoginResult lr, String minecraftJar) {
        this.lr = lr;
        
        this.minecraftJar = minecraftJar;
        v = new Version(ver);
        if(Utils.isEmpty(ver.gameDir)) v.gameDir = Utils.addSeparator(pub.gameDir);
        if(Utils.isEmpty(ver.gameAssets)) v.gameAssets = pub.gameAssets;
        if(Utils.isEmpty(v.gameAssets)) v.gameAssets = v.gameDir + "assets";
        if(Utils.isEmpty(ver.gameLibraries)) v.gameLibraries = pub.gameLibraries;
        if(Utils.isEmpty(ver.height)) v.height = pub.height;
        if(Utils.isEmpty(ver.javaArgs)) v.javaArgs = pub.javaArgs;
        if(Utils.isEmpty(ver.javaDir)) v.javaDir = pub.javaDir;
        if(Utils.isEmpty(ver.mainClass)) v.mainClass = pub.mainClass;
        if(Utils.isEmpty(ver.maxMemory)) v.maxMemory = pub.maxMemory;
        if(Utils.isEmpty(ver.name)) v.name = pub.name;
        if(Utils.isEmpty(ver.width)) v.width = pub.width;
        if(Utils.isEmpty(ver.minecraftArguments)) v.minecraftArguments = pub.minecraftArguments;
    }
    
    public List<String> makeLaunchingCommand() {
        System.out.println("*** Making Minecraft Launching Command ***");
        
        ArrayList<String> res = new ArrayList<String>();
        String java;
        if(!Utils.isEmpty(v.javaDir.trim()))
        {
            if(Utils.os() == OperatingSystems.WINDOWS) {
                File file = new File(Utils.addSeparator(v.javaDir) + "javaw.exe");
                if(file.exists() && file.isFile())
                    java = "javaw.exe";
                else
                    java = "java.exe";
            } else {
                java = "java";
            }
            java = Utils.addSeparator(v.javaDir) + java;
        }
        else
            java = Utils.getJavaDir();
        System.out.println("Java path: " + java);
        res.add(java);
        
        if(!Utils.isEmpty(v.javaArgs.trim()))
        {
            System.out.println("Java args: " + v.javaArgs);
            res.add(v.javaArgs);
        }
        res.add("-Xincgc");
        if(!Utils.isEmpty(v.maxMemory))
        {
            System.out.println("Memory: " + v.maxMemory);
            res.add("-Xmx" + v.maxMemory + "m");
        }
        
        makeJavaLibraryPath(res);
        
        res.add("-Dfml.ignoreInvalidMinecraftCertificates=true");
        res.add("-Dfml.ignorePatchDiscrepancies=true");
        res.add("-Dsun.java2d.noddraw=true");
        res.add("-Dsun.java2d.pmoffscreen=false");
        res.add("-Dsun.java2d.d3d=false");
        res.add("-Dsun.java2d.opengl=false");
        if(Utils.os() != OperatingSystems.WINDOWS)
        {
            String base = new File(v.gameDir).getParent();
            System.out.println("Base folder: " + base);
            res.add("-Duser.home=" + base);
        }
        
        res.add("-cp");
        
        res.add(Utils.parseParams("", Utils.getURL(), File.pathSeparator));
        res.add("org.jackhuang.hellominecraftlauncher.Launcher");
        
        makeSelf(res);
        
        if(!Utils.isEmpty(v.height) && !Utils.isEmpty(v.width))
        {
            res.add("-windowSize=" + v.width + "x" + v.height);
        }
        
        if(v.fullscreen)
            res.add("-windowFullscreen");
        
        if(v.inactiveCoreMods != null && !v.inactiveCoreMods.isEmpty()) {
            String s = "-inactiveCoreMods=" + Utils.parseParams(
                    "",
                    v.inactiveCoreMods.toArray(), File.pathSeparator
            );
            res.add(s);
        }
        
        if(v.inactiveExtMods != null && !v.inactiveExtMods.isEmpty()) {
            String s = "-inactiveExtMods=" + Utils.parseParams(
                    "",
                    v.inactiveExtMods.toArray(), File.pathSeparator);
            res.add(s);
        }
        
        if(!Utils.isEmpty(v.minecraftArguments))
        {
            String[] splitted2 = Utils.tokenize(v.minecraftArguments);
            res.addAll(Arrays.asList(splitted2));
        }
        
        res.add("-gameDir=" + v.gameDir);
        if(SettingsManager.settings.disableMoveMods)
            res.add("-notMove");
        
        return res;
    }
    
    protected abstract void makeJavaLibraryPath(List<String> list);
    
    /**
     * You must do these things:<br />
     * 1 minecraft class path<br />
     * 2 main class<br />
     * 3 minecraft arguments<br />
     * @param list the command list you shoud edit.
     */
    protected abstract void makeSelf(List<String> list);
    
    public Version getUserVersion()
    {
        return v;
    }
}
