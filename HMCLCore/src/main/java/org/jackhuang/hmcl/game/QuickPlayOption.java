/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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

/// The quick play option.
///
/// @see <a href="https://minecraft.wiki/w/Quick_Play">Quick Play - Minecraft Wiki</a>
public sealed interface QuickPlayOption {
    record SinglePlayer(String worldFolderName) implements QuickPlayOption {
    }

    record MultiPlayer(String serverIP) implements QuickPlayOption {
    }

    record Realm(String realmID) implements QuickPlayOption {
    }
}
