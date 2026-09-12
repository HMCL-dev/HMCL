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
package org.jackhuang.hmcl.game;

public record JavaCompatibility(
        int targetMajor,
        int actualMajor,
        Level level
) {
    public enum Level {
        /// The selected runtime matches what the instance expects.
        OK,

        /// The selected runtime is newer than expected.
        ///
        /// This is deliberately not an error: whether the game actually breaks depends on
        /// which mods are installed, and that cannot be determined statically. The correct
        /// response is therefore to tell the user and offer a way back, never to block.
        NEWER_THAN_EXPECTED
    }

    public boolean isOk() {
        return level == Level.OK;
    }
}
