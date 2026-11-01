// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.util.regex.Pattern;

/**
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
        this.name = os.getMojangName();
        this.version = version;
        this.arch = arch;
    }

    public OSRestriction(String name, String version, String arch) {
        this.name = name;
        this.version = version;
        this.arch = arch;
    }

    public boolean allow() {
        // Some modpacks directly use { name: "win-x86" }
        if (name != null) {
            String[] parts = name.split("-", 3);
            if (parts.length == 2) {
                OperatingSystem os = OperatingSystem.parseOSName(parts[0]);
                Architecture arch = Architecture.parseArchName(parts[1]);

                if (os != OperatingSystem.UNKNOWN && arch != Architecture.UNKNOWN) {
                    if (os != OperatingSystem.CURRENT_OS && !(os == OperatingSystem.LINUX && OperatingSystem.CURRENT_OS.isLinuxOrBSD())) {
                        return false;
                    }

                    if (arch != Architecture.SYSTEM_ARCH) {
                        return false;
                    }

                    return true;
                }
            }
        }

        OperatingSystem os = OperatingSystem.parseOSName(name);
        if (os != OperatingSystem.UNKNOWN
                && os != OperatingSystem.CURRENT_OS
                && !(os == OperatingSystem.LINUX && OperatingSystem.CURRENT_OS.isLinuxOrBSD()))
            return false;

        if (version != null)
            if (Lang.test(() -> !Pattern.compile(version).matcher(OperatingSystem.SYSTEM_VERSION.getVersion()).matches()))
                return false;

        if (arch != null) {
            // Aliases such as "x64" are not regexes over checked names.
            Architecture parsedArch = Architecture.parseArchName(arch);
            if (parsedArch != Architecture.UNKNOWN)
                return parsedArch == Architecture.SYSTEM_ARCH;
            return !Lang.test(() -> !Pattern.compile(arch).matcher(Architecture.SYSTEM_ARCH.getCheckedName()).matches());
        }

        return true;
    }
}
