/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.installers;

import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.apis.utils.Compressor;

/**
 *
 * @author hyh
 */
public class PackMinecraftInstaller {

    String dest;
    ArrayList<String> src;

    public PackMinecraftInstaller(ArrayList<String> src, String dest) {
        this.dest = dest;
        this.src = src;
    }

    public void install() {
        String path = Utils.addSeparator(Utils.currentDir())
                + "HMCL-MERGE-TEMP" + File.separator;
        File file = new File(path);
        file.mkdirs();
        for (int i = 0; i < src.size(); i++) {
            Compressor.unzip(src.get(i), path);
        }
        Compressor.zip(path, dest);
        Utils.deleteAll(file);
    }
}
