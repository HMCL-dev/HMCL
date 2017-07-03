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
package org.jackhuang.hmcl.core.install.liteloader;

import java.util.Arrays;
import java.util.Objects;
import org.jackhuang.hmcl.core.install.InstallerVersionList;
import org.jackhuang.hmcl.core.version.MinecraftLibrary;

/**
 *
 * @author huang
 */
public class LiteLoaderInstallerVersion extends InstallerVersionList.InstallerVersion {

    public MinecraftLibrary[] libraries;
    public String tweakClass;
    
    public LiteLoaderInstallerVersion(String selfVersion, String mcVersion) {
        super(selfVersion, mcVersion);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 13 * hash + Arrays.deepHashCode(this.libraries);
        hash = 13 * hash + Objects.hashCode(this.tweakClass);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || !(obj instanceof LiteLoaderVersionList))
            return false;
        if (this == obj)
            return true;
        final LiteLoaderInstallerVersion other = (LiteLoaderInstallerVersion) obj;
        if (!Objects.equals(this.tweakClass, other.tweakClass))
            return false;
        return Arrays.deepEquals(this.libraries, other.libraries);
    }

}
