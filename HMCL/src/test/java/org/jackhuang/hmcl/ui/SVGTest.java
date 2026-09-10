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
package org.jackhuang.hmcl.ui;

import javafx.geometry.Bounds;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// @author Glavo
public final class SVGTest {

    @ParameterizedTest
    @EnumSource(SVG.class)
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testViewWindow(SVG svg) {
        SVGPath path = new SVGPath();
        path.setContent(svg.getPath());

        Bounds layoutBounds = path.getLayoutBounds();
        assertEquals(
                new ViewBox(0, 0, 24, 24),
                new ViewBox(layoutBounds.getMinX(), layoutBounds.getMinY(), layoutBounds.getWidth(), layoutBounds.getHeight())
        );
    }

    private record ViewBox(double minX, double minY, double width, double height) {
    }
}
