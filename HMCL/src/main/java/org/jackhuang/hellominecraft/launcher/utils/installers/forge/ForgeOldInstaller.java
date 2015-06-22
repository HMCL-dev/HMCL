/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.forge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.launcher.utils.installers.PackMinecraftInstaller;

/**
 *
 * @author hyh
 */
public class ForgeOldInstaller {
    
    public static void install(String destMinecraftJar, String srcMinecraftJar, String forgeUniversal) throws IOException {
        ArrayList<String> al = new ArrayList<String>();
        al.add(srcMinecraftJar);
        al.add(forgeUniversal);
        new PackMinecraftInstaller(al, new File(destMinecraftJar)).install();
    }
    
}
