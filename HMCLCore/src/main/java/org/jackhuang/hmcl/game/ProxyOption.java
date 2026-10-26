// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
