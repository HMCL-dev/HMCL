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
package org.jackhuang.hmcl.core.version;

import org.jackhuang.hmcl.core.download.DownloadType;
import org.jackhuang.hmcl.api.game.IMinecraftLibrary;

/**
 *
 * @author huangyuhui
 */
public abstract class AbstractMinecraftLibrary implements IMinecraftLibrary {

    private final String name;

    public AbstractMinecraftLibrary(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    public abstract LibraryDownloadInfo getDownloadInfo();

    @Override
    public String getDownloadURL(String downloadSource) {
        return getDownloadInfo().getUrl(DownloadType.valueOf(downloadSource));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof MinecraftLibrary)
            return ((MinecraftLibrary) obj).getName().equals(name);
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 89 * hash + (this.name != null ? this.name.hashCode() : 0);
        return hash;
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
    }
}
