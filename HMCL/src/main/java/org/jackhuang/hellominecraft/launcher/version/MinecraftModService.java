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
package org.jackhuang.hellominecraft.launcher.version;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftModService;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.utils.ModInfo;
import org.jackhuang.hellominecraft.utils.code.DigestUtils;
import org.jackhuang.hellominecraft.utils.system.FileUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftModService extends IMinecraftModService {

    List<ModInfo> modCache;

    public MinecraftModService(IMinecraftService profile) {
        super(profile);
    }

    @Override
    public List<ModInfo> getMods() {
        if (modCache == null)
            return recacheMods();
        else
            return modCache;
    }

    @Override
    public List<ModInfo> recacheMods() {
        if (service.version().getSelectedVersion() == null)
            return modCache = new ArrayList<>();
        File modsFolder = service.version().getRunDirectory(service.version().getSelectedVersion().id, "mods");
        ArrayList<ModInfo> mods = new ArrayList<>();
        File[] fs = modsFolder.listFiles();
        if (fs != null)
            for (File f : fs)
                if (ModInfo.isFileMod(f)) {
                    ModInfo m = ModInfo.readModInfo(f);
                    if (m != null)
                        mods.add(m);
                } else if (f.isDirectory()) {
                    File[] ss = f.listFiles();
                    if (ss != null)
                        for (File ff : ss)
                            if (ModInfo.isFileMod(ff)) {
                                ModInfo m = ModInfo.readModInfo(ff);
                                if (m != null)
                                    mods.add(m);
                            }
                }
        Collections.sort(mods);
        return modCache = mods;
    }

    @Override
    public boolean addMod(File f) {
        try {
            if (service.version().getSelectedVersion() == null)
                return false;
            if (!ModInfo.isFileMod(f))
                return false;
            File modsFolder = service.version().getRunDirectory(service.version().getSelectedVersion().id, "mods");
            if (modsFolder == null)
                return false;
            modsFolder.mkdirs();
            File newf = new File(modsFolder, f.getName());
            FileUtils.copyFile(f, newf);
            ModInfo i = ModInfo.readModInfo(f);
            modCache.add(i);
            return true;
        } catch (IOException ex) {
            HMCLog.warn("Failed to copy mod", ex);
            return false;
        }
    }

    @Override
    public void removeMod(Object[] rows) {
        if (rows.length == 0)
            return;
        for (Object r : rows)
            if (r instanceof ModInfo)
                ((ModInfo) r).location.delete();
            else if (r instanceof Number)
                getMods().get(((Number) r).intValue()).location.delete();
        recacheMods();
    }

    public String[] checkMd5s() throws IOException {
        String[] res = new String[getMods().size()];
        for (int i = 0; i < res.length; i++)
            res[i] = DigestUtils.md5Hex(new FileInputStream(getMods().get(i).location));
        return res;
    }

}
