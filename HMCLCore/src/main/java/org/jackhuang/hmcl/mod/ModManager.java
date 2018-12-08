/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public final class ModManager {
    private final GameRepository repository;
    private final SimpleMultimap<String, ModInfo> modCache = new SimpleMultimap<String, ModInfo>(HashMap::new, TreeSet::new);

    public ModManager(GameRepository repository) {
        this.repository = repository;
    }

    public void refreshMods(String id) {
        modCache.removeKey(id);
        File modsDirectory = new File(repository.getRunDirectory(id), "mods");
        Consumer<File> puter = modFile -> Lang.ignoringException(() -> modCache.put(id, ModInfo.fromFile(modFile)));
        Optional.ofNullable(modsDirectory.listFiles()).map(Arrays::stream).ifPresent(files -> files.forEach(modFile -> {
            if (modFile.isDirectory() && VersionNumber.isIntVersionNumber(modFile.getName())) {
                // If the folder name is game version, forge will search mod in this subdirectory
                Optional.ofNullable(modFile.listFiles()).map(Arrays::stream).ifPresent(x -> x.forEach(puter));
            } else {
                puter.accept(modFile);
            }
        }));
    }

    public Collection<ModInfo> getMods(String id) {
        if (!modCache.containsKey(id))
            refreshMods(id);
        return modCache.get(id);
    }

    public void addMod(String id, File file) throws IOException {
        if (!ModInfo.isFileMod(file))
            throw new IllegalArgumentException("File " + file + " is not a valid mod file.");

        if (!modCache.containsKey(id))
            refreshMods(id);

        File modsDirectory = new File(repository.getRunDirectory(id), "mods");
        if (!FileUtils.makeDirectory(modsDirectory))
            throw new IOException("Cannot make directory " + modsDirectory);

        File newFile = new File(modsDirectory, file.getName());
        FileUtils.copyFile(file, newFile);

        modCache.put(id, ModInfo.fromFile(newFile));
    }

    public boolean removeMods(String id, ModInfo... modInfos) {
        boolean result = Arrays.stream(modInfos).reduce(true, (acc, modInfo) -> acc && modInfo.getFile().delete(), Boolean::logicalAnd);
        refreshMods(id);
        return result;
    }
}
