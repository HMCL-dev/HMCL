/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.utilities;

import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.settings.Settings;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 *
 * @author hyh
 */
public class SettingsManager {
    
    public static Settings settings;
    static Gson gson;
    
    public static void load() {
        gson = new Gson();
        File file = new File(Utils.addSeparator(Utils.currentDir()) + "settings.json");
        if(file.exists())
        {
            String str = Utils.readToEnd(file);
            if(str == null || str.trim().equals(""))
            {
                init();
            }
            else
            {
                settings = gson.fromJson(str, Settings.class);
            }
        }
        else
        {
            settings = new Settings();
            save();
        }
    }
    
    public static void init()
    {
            settings = new Settings();
            save();
    }
    
    public static void save()
    {
        Utils.writeToFile(new File(Utils.addSeparator(Utils.currentDir()) + "settings.json"), gson.toJson(settings));
    }
    
    public static Version getVersion(String name)
    {
        if(settings == null) return null;
        if(settings.versions == null) return null;
        for(int i = 0; i < settings.versions.size(); i++)
        {
            Version v = settings.versions.get(i);
            if(v == null) continue;
            if(v.name == null) continue;
            if(v.name.equals(name))
                return v;
        }
        return null;
    }
    
    public static void setVersion(Version ver)
    {
        if(ver == null) return;
        if(settings.versions == null)
        {
            settings.versions = new ArrayList<Version>();
        }
        for(int i = 0; i < settings.versions.size(); i++)
        {
            Version v = settings.versions.get(i);
            if(v == null) continue;
            if(v.name == null || v.name.equals(ver.name))
            {
                settings.versions.set(i, ver);
                return;
            }
        }
        settings.versions.add(ver);
    }
    
    public static void delVersion(Version ver) {
        if(settings == null || settings.versions == null)
            return;
        settings.versions.remove(ver);
    }
}
