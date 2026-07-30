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
package org.jackhuang.hmcl.theme;

import org.jetbrains.annotations.NotNullByDefault;

/// Controls how the launcher window is shown while the selected background is loading.
@NotNullByDefault
public enum BackgroundLoadPolicy {
    /// Loads the selected background before the launcher background property is first exposed to the UI.
    WAIT_FOR_BACKGROUND,

    /// Shows the fallback background immediately, then replaces it when the selected background loads.
    SHOW_FALLBACK_WHILE_LOADING
}
