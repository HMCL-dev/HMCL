// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

/**
 * The visibility of launcher.
 * @author huangyuhui
 */
public enum LauncherVisibility {

    /**
     * Close the launcher anyway when the game process created even if failed to
     * launch game.
     */
    CLOSE,

    /**
     * Hide the launcher when the game process created, if failed to launch
     * game, will show the log window.
     */
    HIDE,

    /**
     * Keep the launcher visible even if the game launched successfully.
     */
    KEEP,

    /**
     * Hide the launcher and reopen it when game closes.
     */
    HIDE_AND_REOPEN;

    public boolean isDaemon() {
        return this != CLOSE;
    }
}
