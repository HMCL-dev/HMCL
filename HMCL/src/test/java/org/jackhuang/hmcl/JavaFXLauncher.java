// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

public final class JavaFXLauncher {

    private JavaFXLauncher() {
    }

    private static boolean started = false;

    static {
        // init JavaFX Toolkit
        try {
            javafx.application.Platform.startup(() -> {
            });
            started = true;
        } catch (IllegalStateException e) {
            started = true;
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void start() {
    }

    public static boolean isStarted() {
        return started;
    }
}
