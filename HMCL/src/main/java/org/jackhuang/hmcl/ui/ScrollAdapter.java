// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.application.ConditionalFeature;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.input.ScrollEvent;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Bridges control geometry and native skin dispatch to the shared scrolling policy.
@NotNullByDefault
abstract class ScrollAdapter {
    /// Whether native skins need zero-distance input to animate overlay scrollbar visibility.
    static final boolean TOUCH_SUPPORTED = Platform.isSupported(ConditionalFeature.INPUT_TOUCH);

    /// Base wheel pixel multiplier scaled by the startup `hmcl.scroll.speed` preference.
    private static final double WHEEL_SPEED = readWheelSpeed(
            System.getProperty("hmcl.scroll.speed", System.getenv("HMCL_SCROLL_SPEED")));

    /// Resolves a factor or percentage to a wheel pixel multiplier, using the default for invalid values.
    static double readWheelSpeed(@Nullable String value) {
        final double speedBase = 3.0;
        double speedScale = 1.0;
        try {
            if (StringUtils.isNotBlank(value)) {
                value = value.strip();
                speedScale = value.endsWith("%")
                        ? Double.parseDouble(value.substring(0, value.length() - 1)) / 100.0
                        : Double.parseDouble(value);
            }
        } catch (NumberFormatException ignored) {
        }
        return Double.isFinite(speedScale) && speedScale > 0
                ? speedBase * Math.max(speedScale, 0.0001)
                : speedBase;
    }

    /// Coordinates animation and gestures independently of this control's geometry.
    final ScrollController controller;

    /// Installs common lifecycle observation before the adapter's skin hooks.
    ScrollAdapter(Node node) {
        controller = new ScrollController(node, this);
    }

    /// Returns the scrollable pixel distance available in the requested direction.
    abstract double limit(boolean horizontal, double pixels);

    /// Applies a bounded pixel displacement and returns the distance actually moved.
    abstract double scroll(boolean horizontal, double pixels);

    /// Scales platform wheel pixels consistently, preserving system settings and fractional deltas.
    double wheelDelta(ScrollEvent event, boolean horizontal) {
        return -(horizontal ? event.getDeltaX() : event.getDeltaY()) * WHEEL_SPEED;
    }

    /// Maps continuous input to pixels without applying wheel line or page scaling.
    double gestureDelta(ScrollEvent event, boolean horizontal) {
        return horizontal ? -event.getDeltaX() : -event.getDeltaY();
    }

    /// Preserves a skin's scroll lifecycle without applying its native displacement.
    static ScrollEvent stationaryScroll(ScrollEvent event) {
        return new ScrollEvent(event.getSource(), event.getTarget(), event.getEventType(),
                event.getX(), event.getY(), event.getScreenX(), event.getScreenY(),
                event.isShiftDown(), event.isControlDown(), event.isAltDown(), event.isMetaDown(),
                event.isDirect(), event.isInertia(), 0, 0, 0, 0,
                ScrollEvent.HorizontalTextScrollUnits.NONE, 0,
                ScrollEvent.VerticalTextScrollUnits.NONE, 0, event.getTouchCount(), event.getPickResult());
    }
}
