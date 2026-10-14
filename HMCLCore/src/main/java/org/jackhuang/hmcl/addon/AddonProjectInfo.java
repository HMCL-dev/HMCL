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
package org.jackhuang.hmcl.addon;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

@NotNullByDefault
public abstract class AddonProjectInfo {

    @Range(from = 0, to = Integer.MAX_VALUE)
    private final int mcmodId;
    private final @Nullable String curseforgeId;
    private final @Nullable String modrinthId;

    protected AddonProjectInfo(@Range(from = -1, to = Integer.MAX_VALUE) int mcmodId, @Nullable String curseforgeId, @Nullable String modrinthId) {
        this.mcmodId = mcmodId;
        this.curseforgeId = curseforgeId;
        this.modrinthId = modrinthId;
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
        return curseforgeId != null
                ? "https://www.curseforge.com/minecraft/" + curseforgeClass() + "/" + curseforgeId + "/rarity-system"
                : null;
    }

    public @Nullable String modrinthUrl() {
        return modrinthId != null
                ? "https://modrinth.com/" + modrinthClass() + "/" + modrinthId
                : null;
    }

}
