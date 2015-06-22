/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.utils;

import java.io.File;
import java.io.IOException;

/**
 *
 * @author hyh
 */
public class BaseLauncherProfile {
    public static String profile = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";
    public static void tryWriteProfile(File gameDir) throws IOException {
        File file = new File(gameDir, "launcher_profiles.json");
        if(!file.exists())
            FileUtils.writeStringToFile(file, profile);
    }
}
