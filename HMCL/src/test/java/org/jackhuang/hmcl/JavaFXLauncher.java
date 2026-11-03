// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import javafx.application.Platform;
import org.jackhuang.hmcl.setting.LauncherSettings;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/// Owns the shared JavaFX toolkit for the lifetime of the test process.
@NotNullByDefault
public final class JavaFXLauncher {

    /// Prevents instantiation of the shared toolkit bootstrap.
    private JavaFXLauncher() {
    }

    /// Whether the toolkit is available for JavaFX tests in this process.
    private static boolean started = false;

    static {
        try {
            initializeAnimationPolicy();
            Platform.startup(() -> {
            });
            started = true;
        } catch (IllegalStateException e) {
            started = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        // Closing a test's last Stage must not terminate the toolkit needed by later tests.
        // JavaFX cannot be restarted; the test worker process owns its final shutdown.
        if (started)
            Platform.setImplicitExit(false);
    }

    /// Initializes the cached animation policy before any JavaFX test can select a different default.
    /// Tests that exercise animations explicitly enable them on their controls.
    /// The temporary settings are not persisted and the original configuration is restored.
    private static void initializeAnimationPolicy() throws ReflectiveOperationException {
        Field field = SettingsManager.class.getDeclaredField("launcherSettings");
        field.setAccessible(true);
        @Nullable Object previous = field.get(null);
        LauncherSettings settings = new LauncherSettings();
        settings.animationDisabledProperty().set(true);
        field.set(null, settings);
        try {
            AnimationUtils.init();
        } finally {
            field.set(null, previous);
        }
    }

    /// Triggers initialization of the shared toolkit.
    public static void start() {
    }

    /// Returns whether JavaFX tests can run in this process.
    public static boolean isStarted() {
        return started;
    }
}
