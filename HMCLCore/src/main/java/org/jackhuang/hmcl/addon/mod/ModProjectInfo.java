// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// @author Glavo
@NotNullByDefault
public final class ModProjectInfo extends AddonProjectInfo {

    private final @Nullable Object modIds;

    public ModProjectInfo(
            int mcmodId, String curseforgeId, String modrinthId,
            String mainName, String subName, String abbrName,
            String modIds) {
        super(mcmodId, curseforgeId, modrinthId, mainName, subName, abbrName);

        if (modIds.isEmpty()) {
            this.modIds = null;
        } else if (modIds.indexOf(';') < 0) {
            this.modIds = modIds;
        } else {
            this.modIds = List.<String>of(modIds.split(";"));
        }
    }

    /// Returns an unmodifiable list of recorded mod IDs, or an empty list if none are recorded.
    @SuppressWarnings("unchecked")
    public @Unmodifiable List<String> getModIds() {
        if (modIds == null)
            return List.of();
        if (modIds instanceof String id)
            return List.of(id);
        return (List<String>) modIds;
    }

    @Override
    protected String mcmodClass() {
        return "class";
    }

    @Override
    protected String curseforgeClass() {
        return "mc-mods";
    }

    @Override
    protected String modrinthClass() {
        return "mod";
    }
}
