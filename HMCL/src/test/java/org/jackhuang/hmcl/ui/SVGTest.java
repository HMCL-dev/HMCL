// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
