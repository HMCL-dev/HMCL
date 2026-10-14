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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.AddonProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@NotNullByDefault
public final class ModProjectInfo extends AddonProjectInfo {

    private final @Nullable Object modIds;

    public ModProjectInfo(int mcmodId, @Nullable String curseforgeId, @Nullable String modrinthId) {
        super(mcmodId, curseforgeId, modrinthId);

        this.modIds = null;
    }

    public ModProjectInfo(int mcmodId, @Nullable String curseforgeId, @Nullable String modrinthId, String modId) {
        super(mcmodId, curseforgeId, modrinthId);

        this.modIds = modId;
    }

    public ModProjectInfo(int mcmodId, @Nullable String curseforgeId, @Nullable String modrinthId, List<String> modIds) {
        super(mcmodId, curseforgeId, modrinthId);

        //noinspection ConstantValue
        if (modIds == null || modIds.isEmpty())
            this.modIds = null;
        else if (modIds.size() == 1)
            this.modIds = modIds.get(0);
        else
            this.modIds = List.copyOf(modIds);
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
