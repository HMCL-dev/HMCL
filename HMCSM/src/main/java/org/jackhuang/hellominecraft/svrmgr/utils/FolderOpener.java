/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.utils;

import java.io.File;
import org.jackhuang.hellominecraft.utils.MessageBox;

/**
 *
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
    public static void openMods() {
        open(Utilities.try2GetPath("mods"));
    }
    public static void openCoreMods() {
        open(Utilities.try2GetPath("coremods"));
    }
    public static void openPlugins() {
        open(Utilities.try2GetPath("plugins"));
    }
    public static void openConfig() {
        open(Utilities.try2GetPath("config"));
    }
    
}
