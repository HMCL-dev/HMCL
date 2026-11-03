// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import javafx.application.Platform;
import org.jetbrains.annotations.NotNullByDefault;

/// Owns the shared JavaFX toolkit for the lifetime of the test process.
@NotNullByDefault
public final class JavaFXLauncher {

    /// Prevents instantiation of the shared toolkit bootstrap.
    private JavaFXLauncher() {
    }

    /// Whether the toolkit is available for JavaFX tests in this process.
    private static boolean started = false;

    static {
        // Keep pixel-distance assertions independent of local launcher preferences.
        System.setProperty("hmcl.scroll.speed", "1");
        try {
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

    /// Triggers initialization of the shared toolkit.
    public static void start() {
    }

    /// Returns whether JavaFX tests can run in this process.
    public static boolean isStarted() {
        return started;
    }
}
