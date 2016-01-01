/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.settings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jackhuang.hellominecraftlauncher.apis.utils.IOUtils;
import org.jackhuang.hellominecraftlauncher.apis.utils.MCUtils;
import org.jackhuang.hellominecraftlauncher.apis.utils.OS;

/**
 *
 * @author hyh
 */
public class Config {
    public String last, bgpath, username, clientToken;
    public int logintype, downloadtype;
    public HashMap<String, Version> versions;
    public boolean checkUpdate, disableMoveMods, debugMode;
    private Version publicSettings;
    public HashMap<String, Map> authSettings;
    
    public Version global() {
        return publicSettings;
    }
    
    public Config()
    {
        publicSettings = new Version();
        publicSettings.width = "854";
        publicSettings.height = "480";
        publicSettings.maxMemory = "1024";
        clientToken = UUID.randomUUID().toString();
        username = "Player007";
        logintype = downloadtype = 0;
        checkUpdate = true;
        disableMoveMods = true;
        publicSettings.isVer16 = true;
        switch(OS.os())
        {
            case WINDOWS:
                publicSettings.gameDir = IOUtils.currentDir();
                if(publicSettings.gameDir != null && !publicSettings.gameDir.trim().equals(""))
                    publicSettings.gameDir = IOUtils.addSeparator(publicSettings.gameDir) + MCUtils.minecraft();
                break;
            default:
                publicSettings.gameDir = MCUtils.getLocation().getAbsolutePath();
        }
            
    }
    
    
}
