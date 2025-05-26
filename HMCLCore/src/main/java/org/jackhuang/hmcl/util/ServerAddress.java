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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Glavo
 */
public final class ServerAddress {

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

    private final String host;
    private final int port;

    public ServerAddress(@NotNull String host) {
        this(host, UNKNOWN_PORT);
    }

    public ServerAddress(@NotNull String host, int port) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ServerAddress)) return false;
        ServerAddress that = (ServerAddress) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return String.format("ServerAddress[host='%s', port=%d]", host, port);
    }
}
