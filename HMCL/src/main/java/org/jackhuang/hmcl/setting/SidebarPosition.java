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

/// Represents the placement position of the launcher navigation sidebar drawer.
@NotNullByDefault
public enum SidebarPosition {

    /// Sidebar positioned on the left side of the window (default).
    LEFT("left"),

    /// Sidebar positioned on the right side of the window.
    RIGHT("right");

    private final String name;

    SidebarPosition(String name) {
        this.name = name;
    }

    /// Returns the internal string name of this position.
    public String getName() {
        return name;
    }
}
