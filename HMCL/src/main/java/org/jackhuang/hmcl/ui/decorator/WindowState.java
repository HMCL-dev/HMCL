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
package org.jackhuang.hmcl.ui.decorator;

import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNullByDefault;

/// Applies the main window's platform-specific maximization policy.
/// All methods must be called on the JavaFX application thread.
@NotNullByDefault
public final class WindowState {
    /// Prevents instantiation of this utility class.
    private WindowState() {
    }

    /// Returns whether the launcher honors maximization for the stage's platform and style.
    ///
    /// Transparent windows on macOS do not support maximization in the launcher: native zoom notifications
    /// can mark an ordinary borderless window as maximized, including during initialization.
    /// This policy does not suppress full-screen or iconified states.
    ///
    /// @param stage the main stage to inspect
    /// @return `false` for transparent stages on macOS; `true` otherwise
    public static boolean supportsMaximization(Stage stage) {
        // https://github.com/HMCL-dev/HMCL/issues/4290
        return OperatingSystem.CURRENT_OS != OperatingSystem.MACOS || stage.getStyle() != StageStyle.TRANSPARENT;
    }

    /// Returns whether the stage is maximized under the launcher's platform policy.
    ///
    /// @param stage the main stage to inspect
    /// @return `true` if maximization is supported and the stage reports that it is maximized
    public static boolean isMaximized(Stage stage) {
        return supportsMaximization(stage) && stage.isMaximized();
    }
}
