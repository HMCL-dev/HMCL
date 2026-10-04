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

import javafx.geometry.Insets;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.paint.Color;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests launcher background opacity storage.
@NotNullByDefault
public final class LauncherBackgroundTest {
    /// Verifies opacity changes reuse the unmodified JavaFX background.
    @Test
    public void changesOpacityWithoutChangingBackground() {
        Background background = new Background(
                new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY));
        LauncherBackground changed = new LauncherBackground(background, 0.4);

        assertSame(background, changed.background());
        assertEquals(0.4, changed.opacity());
        assertEquals(1.0, ((Color) changed.background().getFills().get(0).getFill()).getOpacity());
    }

    /// Verifies invalid node opacity values are rejected.
    @Test
    public void rejectsInvalidOpacity() {
        assertThrows(IllegalArgumentException.class, () -> new LauncherBackground(Background.EMPTY, -0.1));
        assertThrows(IllegalArgumentException.class, () -> new LauncherBackground(Background.EMPTY, 1.1));
        assertThrows(IllegalArgumentException.class, () -> new LauncherBackground(Background.EMPTY, Double.NaN));
    }
}
