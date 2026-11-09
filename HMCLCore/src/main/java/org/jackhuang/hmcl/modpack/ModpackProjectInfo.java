// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;

/// @author Glavo
@NotNullByDefault
public final class ModpackProjectInfo extends AddonProjectInfo {
    public ModpackProjectInfo(
            int mcmodId, String curseforgeId, String modrinthId,
            String mainName, String subName, String abbrName
    ) {
        super(mcmodId, curseforgeId, modrinthId, mainName, subName, abbrName);
    }

    @Override
    protected String mcmodClass() {
        return "modpack";
    }

    @Override
    protected String curseforgeClass() {
        return "modpacks";
    }

    @Override
    protected String modrinthClass() {
        return "modpack";
    }
}
