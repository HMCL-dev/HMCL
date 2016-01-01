/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.models;

import org.jackhuang.hellominecraftlauncher.apis.version.MinecraftVersion;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;

/**
 *
 * @author hyh
 */
public class VersionCopier {
    
    public static boolean copy(String gameDir, String fromName, String toName) {
        gameDir = Utils.addSeparator(gameDir);
        File fromDir = new File(gameDir + fromName + File.separator);
        if(!fromDir.exists()) return false;
        File toDir = new File(gameDir + toName + File.separator);
        toDir.mkdirs();
        File fromMinecraftJar = new File(gameDir + fromName + File.separator + fromName + ".jar");
        File toMinecraftJar = new File(gameDir + fromName + File.separator + toName + ".jar");
        File fromMinecraftJson = new File(gameDir + fromName + File.separator + fromName + ".json");
        File toMinecraftJson = new File(gameDir + fromName + File.separator + toName + ".json");
        File fromMinecraftMods = new File(gameDir + fromName + File.separator + "mods" + File.separator);
        File toMinecraftMods = new File(gameDir + fromName + File.separator + "mods" + File.separator);
        File fromMinecraftCoreMods = new File(gameDir + fromName + File.separator + "coremods" + File.separator);
        File toMinecraftCoreMods = new File(gameDir + fromName + File.separator + "coremods" + File.separator);
        File fromMinecraftConfig = new File(gameDir + fromName + File.separator + "config" + File.separator);
        File toMinecraftConfig = new File(gameDir + fromName + File.separator + "config" + File.separator);
        try {
            Utils.copyFile(fromMinecraftJar, toMinecraftJar);
            String str = Utils.readToEnd(fromMinecraftJson);
            Gson gson = new Gson();
            MinecraftVersion v = gson.fromJson(str, MinecraftVersion.class);
            v.id = toName;
            Utils.writeToFile(toMinecraftJson, gson.toJson(v));
            Utils.copyDirectiory(fromMinecraftMods, toMinecraftMods);
            Utils.copyDirectiory(fromMinecraftCoreMods, toMinecraftCoreMods);
            Utils.copyDirectiory(fromMinecraftConfig, toMinecraftConfig);
        } catch (IOException ex) {
            Logger.getLogger(VersionCopier.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        return true;
    }
    
}
