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
package org.jackhuang.hellominecraft.launcher.core.mod;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftModService;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.util.system.FileUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftModService extends IMinecraftModService {

    Map<String, List<ModInfo>> modCache = Collections.synchronizedMap(new HashMap<>());

    public MinecraftModService(IMinecraftService service) {
        super(service);
    }

    @Override
    public List<ModInfo> getMods(String id) {
        if (modCache.containsKey(id))
            return modCache.get(id);
        else
            return recacheMods(id);
    }

    @Override
    public List<ModInfo> recacheMods(String id) {
        File modsFolder = service.version().getRunDirectory(id, "mods");
        ArrayList<ModInfo> mods = new ArrayList<>();
        File[] fs = modsFolder.listFiles();
        if (fs != null)
            for (File f : fs)
                if (ModInfo.isFileMod(f))
                    mods.add(ModInfo.readModInfo(f));
                else if (f.isDirectory()) {
                    File[] ss = f.listFiles();
                    if (ss != null)
                        for (File ff : ss)
                            if (ModInfo.isFileMod(ff))
                                mods.add(ModInfo.readModInfo(ff));
                }
        Collections.sort(mods);
        modCache.put(id, mods);
        return mods;
    }

    @Override
    public boolean addMod(String id, File f) {
        try {
            if (!ModInfo.isFileMod(f))
                return false;
            if (!modCache.containsKey(id))
                recacheMods(id);
            File modsFolder = service.version().getRunDirectory(id, "mods");
            if (!FileUtils.makeDirectory(modsFolder))
                HMCLog.warn("Failed to make directories: " + modsFolder);
            File newf = new File(modsFolder, f.getName());
            FileUtils.copyFile(f, newf);
            ModInfo i = ModInfo.readModInfo(f);
            modCache.get(id).add(i);
            return true;
        } catch (IOException ex) {
            HMCLog.warn("Failed to copy mod", ex);
            return false;
        }
    }

    @Override
    public boolean removeMod(String id, Object[] rows) {
        if (rows.length == 0)
            return true;
        boolean flag = true;
        for (Object r : rows)
            if (r instanceof ModInfo) {
                if (!((ModInfo) r).location.delete()) {
                    HMCLog.warn("Failed to delete mod" + r);
                    flag = false;
                }
            } else if (r instanceof Number)
                if (!getMods(id).get(((Number) r).intValue()).location.delete()) {
                    HMCLog.warn("Failed to delete mod " + r + ", maybe not a file?");
                    flag = false;
                }
        recacheMods(id);
        return flag;
    }

}
