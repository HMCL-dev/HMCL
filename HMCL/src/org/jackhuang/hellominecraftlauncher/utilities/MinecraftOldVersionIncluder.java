/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.utilities;

import org.jackhuang.hellominecraftlauncher.apis.utils.MessageBox;
import org.jackhuang.hellominecraftlauncher.apis.utils.Compressor;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftLibrary;
import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;
import org.jackhuang.hellominecraftlauncher.apis.version.Natives;

/**
 *
 * @author hyh
 */
public class MinecraftOldVersionIncluder {
    Gson gson;
    String path, name;
    
    public MinecraftOldVersionIncluder(String path, String name)
    {
        this.path = path;
        this.name = name;
        this.gson = new Gson();
    }
    
    public void include()
    {
        String inc = Utils.addSeparator(SettingsManager.settings.publicSettings.gameDir);
        String newname = name;
        newname = newname.replace('.', File.separatorChar);
        File file = new File(newname);
        file.mkdirs();
        String pa = Utils.addSeparator(path);
        try
        {
            Utils.copyFile(pa + "bin" + File.separator + "minecraft.jar",
                    inc + "versions" + File.separator + name + File.separator + name + ".jar");
        }
        catch(IOException e)
        {
            e.printStackTrace();
            MessageBox.Show("导入失败：" + e.getMessage());
            return;
        }
        Compressor.zip(pa + "bin" + File.separator + "natives", inc + "libraries" + File.separator +
                newname + File.separator + "natives" + File.separator + "HML" +
                File.separator + "natives-HML-natives-" + Utils.os() + ".jar");
        
        //forge mods
        file = new File(pa + "mods");
        if(file.exists())
            try
            {
                Utils.copyDirectiory(file, new File(inc + "versions" + File.separator + name + File.separator + "mods"));
            }
            catch(IOException e)
            {
                e.printStackTrace();
                MessageBox.Show("移动普通模组失败");
            }
        file = new File(pa + "coremods");
        if(file.exists())
            try
            {
            Utils.copyDirectiory(file, new File(inc + "versions" + File.separator + name + File.separator + "coremods"));
            }
            catch(IOException e)
            {
                e.printStackTrace();
                MessageBox.Show("移动核心模组失败");
            }
        file = new File(pa + "config");
        if(file.exists())
            try
            {
            Utils.copyDirectiory(file, new File(inc + "versions" + File.separator + name + File.separator + "config"));
            }
            catch(IOException e)
            {
                e.printStackTrace();
                MessageBox.Show("移动配置文件失败");
            }
        
        //create json
        MinecraftVersion v = new MinecraftVersion();
        v.id = name;
        v.time = "";
        v.releaseTime = "";
        v.minecraftArguments = "${auth_player_name} ${auth_session} --workDir ${game_directory}";
        v.minimumLauncherVersion = 0;
        v.mainClass = "net.minecraft.client.Minecraft";
        
        ArrayList<String> s = Utils.findAllFile(new File(pa + "bin"));
        ArrayList<MinecraftLibrary> libs = new ArrayList<MinecraftLibrary>();
        MinecraftLibrary lib;
        for(int i = 0; i < s.size(); i++)
        {
            String f = Utils.extractFileName(s.get(i));
            String na = f.substring(0, f.length() - 4);
            if(f != "minecraft.jar")
            {
                file = new File(inc + "libraries" + File.separator + newname + File.separator + na + File.separator + "HML");
                file.mkdirs();
                try
                {
                    Utils.copyFile(pa + "bin" + File.separator + s.get(i), inc + "libraries" + File.separator + newname + File.separator + na + File.separator + "HML" + File.separator + na + "-HML.jar");
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                    MessageBox.Show("复制文件失败：" + s.get(i));
                }
                lib = new MinecraftLibrary();
                lib.name = name + ':' + na + ":HML";
                libs.add(lib);
            }
        }
        lib = new MinecraftLibrary();
        lib.name = name + ":natives:HML";
        Natives n = new Natives();
        n.windows = "natives-windows";
        n.linux = "natives-linux";
        n.osx = "natives-osx";
        lib.natives = n;
        libs.add(lib);
        
        v.libraries = libs;
        
        String c = gson.toJson(v);
        Utils.writeToFile(new File(inc + "versions" + File.separator + name + File.separator + name + ".json"), c);
    }
}
