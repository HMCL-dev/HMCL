/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers.forge;

import java.io.File;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;

/**
 *
 * @author hyh
 */
public class BaseLauncherProfile {
    public static String profile = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";
    public static void tryWriteProfile(String gameDir) {
        File file = new File(Utils.addSeparator(gameDir) + "launcher_profiles.json");
        if(!file.exists())
            Utils.writeToFile(file, profile);
    }
}
