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

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/// @author Glavo
public sealed interface ProxyOption {
    final class Direct implements ProxyOption {
        public static final Direct INSTANCE = new Direct();

        private Direct() {
        }
    }

    final class Default implements ProxyOption {
        public static final Default INSTANCE = new Default();

        private Default() {
        }
    }

    record Http(@NotNull String host, int port, @Nullable String username,
                @Nullable String password) implements ProxyOption {
        public Http {
            if (StringUtils.isBlank(host)) {
                throw new IllegalArgumentException("Host cannot be blank");
            }
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("Illegal port: " + port);
            }
        }
    }

    record Socks(@NotNull String host, int port, @Nullable String username,
                 @Nullable String password) implements ProxyOption {
        public Socks {
            if (StringUtils.isBlank(host)) {
                throw new IllegalArgumentException("Host cannot be blank");
            }
            if (port < 0 || port > 0xFFFF) {
                throw new IllegalArgumentException("Illegal port: " + port);
            }
        }
    }
}
