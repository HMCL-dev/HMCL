// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.animation;

import javafx.scene.Node;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

/**
 * @author Glavo
 */
public final class AnimationUtils {

    private AnimationUtils() {
    }

    /**
     * Trigger initialization of this class.
     * Should be called after settings have been loaded.
     */
    public static void init() {
    }

    private static final boolean ENABLED = !settings().isAnimationDisabled();

    /// Enables window animations except on Unix-like desktops other than macOS.
    private static final boolean PLAY_WINDOW_ANIMATION = ENABLED
            && (SystemInfo.CURRENT_OS == OperatingSystem.MACOS || !SystemInfo.CURRENT_OS.isUnixLike());

    public static boolean isAnimationEnabled() {
        return ENABLED;
    }

    public static boolean playWindowAnimation() {
        return PLAY_WINDOW_ANIMATION;
    }

    public static void reset(Node node, boolean opaque) {
        node.setTranslateX(0);
        node.setTranslateY(0);
        node.setScaleX(1);
        node.setScaleY(1);
        node.setOpacity(opaque ? 1 : 0);
    }
}
