/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 *
 * @author hyh
 */
public class MinecraftOldLoader extends IMinecraftLoader {
    
    public MinecraftOldLoader(Version pub, Version ver, LoginResult lr, String minecraftJar)
    {
        super(pub, ver, lr, minecraftJar);
    }

    @Override
    protected void makeJavaLibraryPath(List<String> res) {
        res.add("-Djava.library.path=" + v.gameDir + "bin" + File.separator + "natives");
    }
    
    @Override
    protected void makeSelf(List<String> res)
    {
        String library = "-cp=";
        if(v.firstLoadLibraries != null)
            for(int i = 0; i < v.firstLoadLibraries.size(); i++)
                if(v.firstLoadLibrariesIsActive == null || v.firstLoadLibrariesIsActive.get(i))
                    library += v.firstLoadLibraries.get(i) + File.pathSeparator;
        library += v.gameDir + "bin" + File.separator + "jinput.jar" + File.pathSeparator +
                v.gameDir + "bin" + File.separator + "lwjgl.jar" + File.pathSeparator +
                v.gameDir + "bin" + File.separator + "lwjgl_util.jar" + File.pathSeparator;
        library += minecraftJar + File.separator;
        if(v.lastLoadLibraries != null)
            for(int i = 0; i < v.lastLoadLibraries.size(); i++)
                if(v.lastLoadLibrariesIsActive == null || v.lastLoadLibrariesIsActive.get(i))
                    library += v.lastLoadLibraries.get(i) + File.pathSeparator;
        library = library.substring(0, library.length() - File.pathSeparator.length());
        res.add(library);
        String mainClass;
        if(Utils.isEmpty(v.mainClass))
            mainClass = "net.minecraft.client.Minecraft";
        else
            mainClass = v.mainClass;
        System.out.println("Main class: " +  mainClass);
        res.add("-mainClass=" + mainClass);
        
        res.add(lr.username);
        res.add(lr.session);
        
        res.add("--workDir");
        res.add(v.gameDir);
    }
}
