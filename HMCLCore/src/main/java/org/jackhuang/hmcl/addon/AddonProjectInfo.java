// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/// @author Glavo
@NotNullByDefault
public abstract class AddonProjectInfo {

    @Range(from = 0, to = Integer.MAX_VALUE)
    private final int mcmodId;
    private final String curseforgeId;
    private final String modrinthId;

    private final String mainName;
    private final String subName;
    private final String abbrName;

    /// This field is typically used to keep a reference to the `List<AddonProjectInfo>` that stores this object,
    /// so that the cached `List<AddonProjectInfo>` is not collected while the `AddonProjectInfo` object itself
    /// has not yet been collected, which could result in multiple identical `AddonProjectInfo` objects on the heap.
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private @Nullable Object keepAlive;

    protected AddonProjectInfo(
            int mcmodId, String curseforgeId, String modrinthId,
            String mainName, String subName, String abbrName) {
        if (mainName.isEmpty()) {
            throw new IllegalArgumentException("main name must not be empty");
        }

        this.mcmodId = mcmodId;
        this.curseforgeId = curseforgeId;
        this.modrinthId = modrinthId;
        this.mainName = mainName;
        this.subName = subName;
        this.abbrName = abbrName;
    }

    /// Returns the recorded CurseForge identifier, possibly empty.
    public String curseforgeId() {
        return curseforgeId;
    }

    /// Returns the recorded primary name.
    public String mainName() {
        return mainName;
    }

    /// Returns the recorded secondary name, possibly empty.
    public String subName() {
        return subName;
    }

    /// Returns the recorded abbreviation, possibly empty.
    public String abbrName() {
        return abbrName;
    }

    /// Returns the primary name with a nonblank abbreviation in brackets and a nonblank
    /// secondary name in parentheses. Only the abbreviation is trimmed.
    public String displayName() {
        StringBuilder builder = new StringBuilder();
        if (!abbrName.isBlank())
            builder.append('[').append(abbrName.trim()).append("] ");
        builder.append(mainName);
        if (!subName.isBlank())
            builder.append(" (").append(subName).append(')');
        return builder.toString();
    }

    public void setKeepAlive(@Nullable Object keepAlive) {
        this.keepAlive = keepAlive;
    }

    protected abstract String mcmodClass();

    protected abstract String curseforgeClass();

    protected abstract String modrinthClass();

    public @Nullable String mcmodUrl() {
        return mcmodId > 0
                ? "https://www.mcmod.cn/" + mcmodClass() + "/" + mcmodId + ".html"
                : null;
    }

    public @Nullable String curseforgeUrl() {
        return !curseforgeId.isEmpty()
                ? "https://www.curseforge.com/minecraft/" + curseforgeClass() + "/" + curseforgeId
                : null;
    }

    public @Nullable String modrinthUrl() {
        return !modrinthId.isEmpty()
                ? "https://modrinth.com/" + modrinthClass() + "/" + modrinthId
                : null;
    }

}
