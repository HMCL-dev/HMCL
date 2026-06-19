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
package org.jackhuang.hmcl.setting;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes whether a settings file can be read and safely overwritten by this HMCL version.
@NotNullByDefault
public enum SettingFileAccess {
    /// The file can be read and overwritten safely.
    READ_WRITE,

    /// The file can be read but must not be overwritten automatically.
    READ_ONLY,

    /// The file cannot be read safely and must not be overwritten automatically.
    UNREADABLE;

    /// Returns whether this access mode permits saving the file.
    public boolean canSave() {
        return this == READ_WRITE;
    }

    /// Returns whether this access mode blocks normal editing of the owning feature.
    public boolean blocksEditing() {
        return this != READ_WRITE;
    }
}
