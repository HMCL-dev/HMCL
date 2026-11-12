// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.VirtualFlow;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

/// Installs smooth wheel scrolling while preserving native gesture and skin behavior.
@NotNullByDefault
public final class ScrollUtils {
    /// Identifies the controller installed on a node, making installation idempotent.
    private static final Object CONTROLLER_KEY = new Object();

    public static void installSmoothScrolling(ScrollPane scrollPane) {
        if (AnimationUtils.isAnimationEnabled())
            addSmoothScrolling(scrollPane);
    }

    public static void installSmoothScrolling(VirtualFlow<?> virtualFlow) {
        if (AnimationUtils.isAnimationEnabled())
            addSmoothScrolling(virtualFlow);
    }

    /// Installs smooth wheel scrolling and direct pixel-based gestures on the FX thread.
    @VisibleForTesting
    public static void addSmoothScrolling(ScrollPane scrollPane) {
        if (controller(scrollPane) == null)
            scrollPane.getProperties().put(CONTROLLER_KEY, new ScrollPaneAdapter(scrollPane).controller);
    }

    /// Installs scrolling on either flow orientation, preserving native cross-axis scrolling.
    @VisibleForTesting
    public static void addSmoothScrolling(VirtualFlow<?> virtualFlow) {
        if (controller(virtualFlow) == null)
            virtualFlow.getProperties().put(CONTROLLER_KEY, new VirtualFlowAdapter(virtualFlow).controller);
    }

    /// Returns an installed controller without allocating a property map on ordinary nodes.
    static @Nullable ScrollController controller(Node node) {
        return node.hasProperties() && node.getProperties().get(CONTROLLER_KEY) instanceof ScrollController controller
                ? controller : null;
    }

    /// Prevents construction of this utility class.
    private ScrollUtils() {
    }

}
