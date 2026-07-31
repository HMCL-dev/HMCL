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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Tests pixel-based smooth scroll target calculations.
@NotNullByDefault
public final class ScrollUtilsTest {
    /// Verifies that a platform pixel delta is mapped through a custom normalized range.
    @Test
    public void mapsPixelDeltaToNormalizedScrollValue() {
        assertEquals(
                17.0,
                ScrollUtils.scrollTargetValue(15.0, -80.0, 10.0, 20.0, 400.0),
                0.000001
        );
    }

    /// Verifies that accumulated targets stop at both content boundaries.
    @Test
    public void clampsScrollTargetsToContentBoundaries() {
        assertEquals(0.0, ScrollUtils.scrollTargetValue(0.1, 80.0, 0.0, 1.0, 400.0), 0.000001);
        assertEquals(1.0, ScrollUtils.scrollTargetValue(0.9, -80.0, 0.0, 1.0, 400.0), 0.000001);
    }

    /// Verifies that resizing content preserves the target's pixel offset rather than its normalized value.
    @Test
    public void retargetsScrollValueAfterContentResize() {
        assertEquals(
                0.25,
                ScrollUtils.retargetScrollValue(0.5, 400.0, 800.0, 0.0, 1.0),
                0.000001
        );
        assertEquals(
                1.0,
                ScrollUtils.retargetScrollValue(0.75, 800.0, 300.0, 0.0, 1.0),
                0.000001
        );
    }

    /// Verifies that an axis without a usable pixel span remains unchanged.
    @Test
    public void leavesUnscrollableAxisUnchanged() {
        assertEquals(0.4, ScrollUtils.scrollTargetValue(0.4, -80.0, 0.0, 1.0, 0.0), 0.000001);
    }
}
