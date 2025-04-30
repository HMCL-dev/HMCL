/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.regex.Pattern;

/**
 *
 * @author huangyuhui
 */
public final class OSRestriction {

    private final String name;
    private final String version;
    private final String arch;

    public OSRestriction() {
        this(OperatingSystem.UNKNOWN);
    }

    public OSRestriction(OperatingSystem os) {
        this(os, null);
    }

    public OSRestriction(OperatingSystem os, String version) {
        this(os, version, null);
    }

    public OSRestriction(OperatingSystem os, String version, String arch) {
        this.name = os.getOfficialName();
        this.version = version;
        this.arch = arch;
    }

    public OSRestriction(String name, String version, String arch) {
        this.name = name;
        this.version = version;
        this.arch = arch;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getArch() {
        return arch;
    }

    public boolean allow() {
        OperatingSystem os = OperatingSystem.parseOSName(name);
        if (os != OperatingSystem.UNKNOWN
                && os != OperatingSystem.CURRENT_OS
                && !(os == OperatingSystem.LINUX && OperatingSystem.CURRENT_OS.isLinuxOrBSD()))
            return false;

        if (version != null)
            if (Lang.test(() -> !Pattern.compile(version).matcher(OperatingSystem.SYSTEM_VERSION).matches()))
                return false;

        if (arch != null)
            return !Lang.test(() -> !Pattern.compile(arch).matcher(Architecture.SYSTEM_ARCH.getCheckedName()).matches());

        return true;
    }

}
