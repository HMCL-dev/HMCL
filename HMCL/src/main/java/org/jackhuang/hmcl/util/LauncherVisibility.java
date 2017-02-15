/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

/**
 *
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
    KEEP;
}
