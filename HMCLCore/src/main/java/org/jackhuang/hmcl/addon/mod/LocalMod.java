/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon.mod;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LocalMod {

    private final String id;
    private final ModLoaderType modLoaderType;
    // Mutated on IO threads (ModManager.refresh/addModInfo under its lock) while iterated lock-free on
    // the FX thread (dependency panel, active-change listeners) — must be concurrent sets, or an
    // in-flight refresh corrupts an FX-side iteration (ConcurrentModificationException at best).
    private final Set<LocalModFile> files = ConcurrentHashMap.newKeySet();
    private final Set<LocalModFile> oldFiles = ConcurrentHashMap.newKeySet();

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

    public Set<LocalModFile> getFiles() {
        return files;
    }

    public Set<LocalModFile> getOldFiles() {
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
