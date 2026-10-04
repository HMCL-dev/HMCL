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

import javafx.scene.layout.Background;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// A launcher background and the opacity applied by its rendering node.
///
/// @param background the JavaFX background rendered by the launcher
/// @param opacity     the node opacity in the range `[0, 1]`
@NotNullByDefault
public record LauncherBackground(Background background, double opacity) {
    /// Creates a launcher background value.
    public LauncherBackground {
        Objects.requireNonNull(background);
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("Launcher background opacity must be between 0 and 1: " + opacity);
        }
    }

}
