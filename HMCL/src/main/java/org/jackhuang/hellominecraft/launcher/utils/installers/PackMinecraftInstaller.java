/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.utils.Compressor;
import org.jackhuang.hellominecraft.utils.FileUtils;
import org.jackhuang.hellominecraft.utils.IOUtils;

/**
 *
 * @author hyh
 */
public class PackMinecraftInstaller {

    File dest;
    ArrayList<String> src;

    public PackMinecraftInstaller(ArrayList<String> src, File dest) {
        this.dest = dest;
        this.src = src;
    }

    public void install() throws IOException {
        File file = new File(IOUtils.currentDir(), "HMCL-MERGE-TEMP");
        file.mkdirs();
        for (String src1 : src) Compressor.unzip(new File(src1), file);
        Compressor.zip(file, dest);
        FileUtils.deleteDirectory(file);
    }
}
