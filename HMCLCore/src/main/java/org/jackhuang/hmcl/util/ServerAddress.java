// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Glavo
 */
public record ServerAddress(String host, int port) {

    private static final int UNKNOWN_PORT = -1;

    private static IllegalArgumentException illegalAddress(String address) {
        return new IllegalArgumentException("Invalid server address: " + address);
    }

    /**
     * @throws IllegalArgumentException if the address is not a valid server address
     */
    public static @NotNull ServerAddress parse(@NotNull String address) {
        Objects.requireNonNull(address);

        if (!address.startsWith("[")) {
            int colonPos = address.indexOf(':');
            if (colonPos >= 0) {
                if (colonPos == address.length() - 1)
                    throw illegalAddress(address);

                String host = address.substring(0, colonPos);
                int port;
                try {
                    port = Integer.parseInt(address.substring(colonPos + 1));
                } catch (NumberFormatException e) {
                    throw illegalAddress(address);
                }
                if (port < 0 || port > 0xFFFF)
                    throw illegalAddress(address);
                return new ServerAddress(host, port);
            } else {
                return new ServerAddress(address);
            }
        } else {
            // Parse IPv6 address
            int colonIndex = address.indexOf(':');
            int closeBracketIndex = address.lastIndexOf(']');

            if (colonIndex < 0 || closeBracketIndex < colonIndex)
                throw illegalAddress(address);

            String host = address.substring(1, closeBracketIndex);
            if (closeBracketIndex == address.length() - 1)
                return new ServerAddress(host);

            if (address.length() < closeBracketIndex + 3 || address.charAt(closeBracketIndex + 1) != ':')
                throw illegalAddress(address);

            int port;
            try {
                port = Integer.parseInt(address.substring(closeBracketIndex + 2));
            } catch (NumberFormatException e) {
                throw illegalAddress(address);
            }

            if (port < 0 || port > 0xFFFF)
                throw illegalAddress(address);

            return new ServerAddress(host, port);
        }
    }

    public ServerAddress(@NotNull String host) {
        this(host, UNKNOWN_PORT);
    }

    public ServerAddress(@NotNull String host, int port) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    @Override
    public @NotNull String toString() {
        return String.format("ServerAddress[host='%s', port=%d]", host, port);
    }
}
