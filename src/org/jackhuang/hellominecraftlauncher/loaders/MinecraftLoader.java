/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.loaders;

import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftLibrary;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import java.io.File;
import java.util.List;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginResult;

/**
 *
 * @author hyh
 */
public class MinecraftLoader extends IMinecraftLoader {
    private MinecraftVersion version;
    String text;
    
    public MinecraftLoader(MinecraftVersion mvt, Version pub, Version ver, LoginResult lr, String minecraftJar)
    {
        super(pub, ver, lr, minecraftJar);
        version = mvt;
        for(int i = 0; i < version.libraries.size(); i++)
        {
            MinecraftLibrary l = version.libraries.get(i);
            String str = l.name;
            String[] s = str.split(":");
            str = s[0];
            str = str.replace('.', File.separatorChar);
            if(l.natives == null)
                str += File.separator + s[1] + File.separator + s[2] +
                    File.separator + s[1] + '-' + s[2] + ".jar";
            else
            {
                str += File.separator + s[1] + File.separator + s[2] +
                    File.separator + s[1] + '-' + s[2] + '-';
                str += l.getNative();
                str += ".jar";
            }
            l.formatted = str;
            version.libraries.set(i, l);
        }
    }

    @Override
    protected void makeJavaLibraryPath(List<String> res) {
        String library = "-Djava.library.path=";
        library += Utils.addSeparator(v.gameDir) + "versions" +
                File.separator + getVersion().id + File.separator + getVersion().id +
                "-natives";
        res.add(library);
    }
    
    @Override
    protected void makeSelf(List<String> res)
    {
        String realpa = Utils.addSeparator(v.gameDir);
        String library = "-cp=";
        String librariespath = realpa + "libraries" + File.separator;
        if(!v.gameLibraries.equals("")) librariespath = v.gameLibraries;
        if(v.firstLoadLibraries != null)
            for(int i = 0; i < v.firstLoadLibraries.size(); i++)
                if(v.firstLoadLibrariesIsActive == null || v.firstLoadLibrariesIsActive.get(i))
                    library += v.firstLoadLibraries.get(i) + File.pathSeparator;
        for(int i = 0; i < getVersion().libraries.size(); i++)
        {
            MinecraftLibrary l = getVersion().libraries.get(i);
            if(l.allow())
            {
                library += librariespath + l.formatted + File.pathSeparator;
            }
        }
        library += minecraftJar + File.pathSeparator;
        if(v.lastLoadLibraries != null)
            for(int i = 0; i < v.lastLoadLibraries.size(); i++)
                if(v.lastLoadLibrariesIsActive == null || v.lastLoadLibrariesIsActive.get(i))
                    library += v.lastLoadLibraries.get(i) + File.pathSeparator;
        library = library.substring(0, library.length() - File.pathSeparator.length());
        res.add(library);
        String mainClass;
        if(Utils.isEmpty(v.mainClass))
            mainClass = getVersion().mainClass;
        else
            mainClass = v.mainClass;
        System.out.println("Main class: " +  mainClass);
        res.add("-mainClass=" + mainClass);
        
        
        String arg = getVersion().minecraftArguments;
        String[] splitted = Utils.tokenize(arg);
        for(String t : splitted) {
            t = t.replace("${auth_player_name}", lr.username);
            t = t.replace("${auth_session}", lr.session);
            t = t.replace("${auth_uuid}", lr.userId);
            t = t.replace("${version_name}", getVersion().id);
            t = t.replace("${game_directory}", v.gameDir);
            t = t.replace("${game_assets}", v.gameAssets);
            t = t.replace("${assets_root}", v.gameAssets);
            t = t.replace("${auth_access_token}", lr.accessToken);
            t = t.replace("${user_type}", lr.userType);
            if(getVersion().assets != null)
                t = t.replace("${assets_index_name}", getVersion().assets);
            t = t.replace("${user_properties}", lr.userProperties);
            res.add(t);
        }
        
        if(res.indexOf("--gameDir") != -1 && res.indexOf("--workDir") != -1) {
            res.add("--workDir");
            res.add(v.gameDir);
        }
        
        res.add("-ver16");
        res.add("-name=" + v.name);
    }

    /**
     * @return the version
     */
    public MinecraftVersion getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(MinecraftVersion version) {
        this.version = version;
    }
}
