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
package org.jackhuang.hellominecraft.launcher.core.version;

import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;
import org.jackhuang.hellominecraft.util.system.OS;
import org.jackhuang.hellominecraft.util.system.Platform;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLibrary extends IMinecraftLibrary {

    public ArrayList<Rules> rules;
    public String url;
    public transient String formatted = null;
    public Natives natives;
    public Extract extract;

    public MinecraftLibrary(String name) {
        super(name);
    }

    public MinecraftLibrary(ArrayList<Rules> rules, String url, Natives natives, String name, Extract extract) {
        super(name);
        this.rules = rules == null ? null : (ArrayList<Rules>) rules.clone();
        this.url = url;
        this.natives = natives == null ? null : (Natives) natives.clone();
        this.extract = extract == null ? null : (Extract) extract.clone();
    }

    /**
     * is the library allowed to load.
     *
     * @return
     */
    @Override
    public boolean allow() {
        if (rules != null) {
            String action = "disallow";
            for (Rules r : rules)
                if (r.action() != null)
                    action = r.action();
            return "allow".equals(action);
        }
        return true;
    }

    private String formatArch(String nati) {
        return nati == null ? "" : nati.replace("${arch}", Platform.getPlatform().getBit());
    }

    private String getNative() {
        switch (OS.os()) {
        case WINDOWS:
            return formatArch(natives.windows);
        case OSX:
            return formatArch(natives.osx);
        default:
            return formatArch(natives.linux);
        }
    }

    @Override
    public boolean isRequiredToUnzip() {
        return natives != null && allow();
    }

    @Override
    public void init() {
        String[] s = name.split(":");
        StringBuilder sb = new StringBuilder(s[0].replace('.', '/')).append('/').append(s[1]).append('/').append(s[2]).append('/').append(s[1]).append('-').append(s[2]);
        if (natives != null)
            sb.append('-').append(getNative());
        formatted = sb.append(".jar").toString();
    }

    @Override
    public File getFilePath(File gameDir) {
        return new File(gameDir, "libraries/" + formatted);
    }

    @Override
    public String getDownloadURL(String urlBase, DownloadType downloadType) {
        if (StrUtils.isNotBlank(url) && downloadType.getProvider().isAllowedToUseSelfURL())
            urlBase = this.url;
        if (urlBase.endsWith(".jar"))
            return urlBase;
        return urlBase + formatted.replace('\\', '/');
    }

    @Override
    public Extract getDecompressExtractRules() {
        return extract == null ? new Extract() : extract;
    }
}
