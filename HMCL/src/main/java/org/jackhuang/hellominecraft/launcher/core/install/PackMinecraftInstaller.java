/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.launcher.core.install;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.util.system.Compressor;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
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
        for (String src1 : src)
            Compressor.unzip(new File(src1), file);
        Compressor.zip(file.getAbsolutePath(), dest.getAbsolutePath());
        FileUtils.deleteDirectory(file);
    }
}
