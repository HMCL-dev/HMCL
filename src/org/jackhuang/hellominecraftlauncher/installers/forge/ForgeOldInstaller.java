/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers.forge;

import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.installers.PackMinecraftInstaller;

/**
 *
 * @author hyh
 */
public class ForgeOldInstaller {
    
    public static void install(String destMinecraftJar, String srcMinecraftJar, String forgeUniversal) {
        ArrayList<String> al = new ArrayList<String>();
        al.add(srcMinecraftJar);
        al.add(forgeUniversal);
        new PackMinecraftInstaller(al, destMinecraftJar).install();
    }
    
}
