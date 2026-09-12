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

import javafx.scene.input.ScrollEvent;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests pixel-based smooth scroll target calculations.
@NotNullByDefault
public final class ScrollUtilsTest {
    /// Verifies that pixel-unit input retains approximately the former friction chain's total distance.
    @Test
    public void calibratesPixelUnitScrollDistance() {
        ScrollEvent mouseWheelEvent = pixelScrollEvent(-32.0);
        ScrollEvent trackPadEvent = pixelScrollEvent(-5.0);

        assertEquals(4.0, ScrollUtils.pixelScrollScale(mouseWheelEvent, 1.0, 1.0), 0.000001);
        assertEquals(4.0, ScrollUtils.pixelScrollScale(trackPadEvent, 1.0, 1.0), 0.000001);
        assertEquals(4.0 / 7.0, ScrollUtils.pixelScrollScale(trackPadEvent, 1.0, 7.0), 0.000001);
    }

    /// Verifies that touch and inertial input remain on the platform's immediate scrolling path.
    @Test
    public void bypassesSmoothTransitionForPlatformGestures() {
        assertTrue(ScrollUtils.shouldSmoothScroll(pixelScrollEvent(-32.0)));
        assertFalse(ScrollUtils.shouldSmoothScroll(pixelScrollEvent(-5.0, true, false, 0)));
        assertFalse(ScrollUtils.shouldSmoothScroll(pixelScrollEvent(-5.0, false, false, 1)));
        assertFalse(ScrollUtils.shouldSmoothScroll(pixelScrollEvent(-5.0, false, true, 0)));
    }

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

    /// Creates a vertical pixel-unit scroll event.
    ///
    /// @param deltaY the platform vertical delta
    /// @return the scroll event
    private static ScrollEvent pixelScrollEvent(double deltaY) {
        return pixelScrollEvent(deltaY, false, false, 0);
    }

    /// Creates a vertical pixel-unit scroll event with the supplied gesture metadata.
    ///
    /// @param deltaY     the platform vertical delta
    /// @param direct     whether the device directly manipulates the content
    /// @param inertia    whether the event continues a completed gesture
    /// @param touchCount the number of touch points producing the event
    /// @return the scroll event
    private static ScrollEvent pixelScrollEvent(double deltaY, boolean direct, boolean inertia, int touchCount) {
        return new ScrollEvent(
                ScrollEvent.SCROLL,
                0.0,
                0.0,
                0.0,
                0.0,
                false,
                false,
                false,
                false,
                direct,
                inertia,
                0.0,
                deltaY,
                0.0,
                deltaY,
                ScrollEvent.HorizontalTextScrollUnits.NONE,
                0.0,
                ScrollEvent.VerticalTextScrollUnits.NONE,
                0.0,
                touchCount,
                null
        );
    }
}
