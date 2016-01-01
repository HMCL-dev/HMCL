/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.utilities;

import java.io.File;
import org.jackhuang.hellominecraftlauncher.apis.utils.MessageBox;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.settings.Version;

/**
 * 打开资源管理器
 * @author jack
 */
public class FolderOpener {
    
    public static void open(String s) {
        try {
            File f = new File(s);
            f.mkdirs();
            java.awt.Desktop.getDesktop().open(f);
        } catch (Exception ex) {
            MessageBox.Show("无法打开资源管理器: " + ex.getMessage());
        }
    }
    
    public static void openResourcePacks(String gameDir) {
        open(gameDir + "resourcepacks");
    }
    public static void openTextutrePacks(String gameDir) {
        open(gameDir + "texturepacks");
    }
    public static void openMods(Version v) {
        open(Utils.try2GetPath(v, "mods", SettingsManager.settings.publicSettings.gameDir));
    }
    public static void openCoreMods(Version v) {
        open(Utils.try2GetPath(v, "coremods", SettingsManager.settings.publicSettings.gameDir));
    }
    public static void openModDir(Version v) {
        open(Utils.try2GetPath(v, "moddir", SettingsManager.settings.publicSettings.gameDir));
    }
    public static void openConfig(Version v) {
        open(Utils.try2GetPath(v, "config", SettingsManager.settings.publicSettings.gameDir));
    }
    
}
