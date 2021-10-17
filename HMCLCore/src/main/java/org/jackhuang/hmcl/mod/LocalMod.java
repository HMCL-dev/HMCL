/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.mod;

import java.util.HashSet;
import java.util.Objects;

public class LocalMod {

    private final String id;
    private final ModLoaderType modLoaderType;
    private final HashSet<LocalModFile> files = new HashSet<>();
    private final HashSet<LocalModFile> oldFiles = new HashSet<>();

    public LocalMod(String id, ModLoaderType modLoaderType) {
        this.id = id;
        this.modLoaderType = modLoaderType;
    }

    public String getId() {
        return id;
    }

    public ModLoaderType getModLoaderType() {
        return modLoaderType;
    }

    public HashSet<LocalModFile> getFiles() {
        return files;
    }

    public HashSet<LocalModFile> getOldFiles() {
        return oldFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalMod localMod = (LocalMod) o;
        return Objects.equals(id, localMod.id) && modLoaderType == localMod.modLoaderType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, modLoaderType);
    }
}
