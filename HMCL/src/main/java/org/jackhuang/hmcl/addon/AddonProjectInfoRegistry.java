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

import org.jackhuang.hmcl.addon.mod.ModProjectInfo;
import org.jetbrains.annotations.NotNullByDefault;

import java.lang.ref.SoftReference;
import java.util.List;

/// @author Glavo
@NotNullByDefault
public abstract class AddonProjectInfoRegistry<I extends AddonProjectInfo> {
    public static final AddonProjectInfoRegistry<ModProjectInfo> MOD = new AddonProjectInfoRegistry<>() {
        @Override
        protected int getFieldsCount() {
            return 5;
        }

        @Override
        protected ModProjectInfo parseAddonProjectInfo(String[] fields) {
            return null;
        }
    };

    protected static final int MCMOD_ID_INDEX = 0;
    protected static final int CURSEFORGE_ID_INDEX = 1;
    protected static final int MODRINTH_ID_INDEX = 2;

    protected abstract int getFieldsCount();

    protected abstract I parseAddonProjectInfo(String[] fields);
}
