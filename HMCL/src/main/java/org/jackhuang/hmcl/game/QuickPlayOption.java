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

import java.util.function.BiConsumer;

public record QuickPlayOption(Type type, String target) {

    public void applyTo(LaunchOptions.Builder builder) {
        this.type.apply(builder, this.target);
    }

    public enum Type {
        SINGLEPLAYER(LaunchOptions.Builder::setWorldFolderName),
        MULTIPLAYER(LaunchOptions.Builder::setServerIp),
        REALM(LaunchOptions.Builder::setRealmID);

        private final BiConsumer<LaunchOptions.Builder, String> setter;

        Type(BiConsumer<LaunchOptions.Builder, String> setter) {
            this.setter = setter;
        }

        private void apply(LaunchOptions.Builder builder, String target) {
            setter.accept(builder, target);
        }
    }
}
