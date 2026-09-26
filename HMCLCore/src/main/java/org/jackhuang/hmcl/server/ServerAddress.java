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
package org.jackhuang.hmcl.server;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Objects;

public record ServerAddress(
        String host,
        int port
) {
    public static int parsePort(final String str) {
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception var2) {
            return 25565;
        }
    }

    public static ServerAddress parseAddress(String ipStr) throws IOException {
        if (ipStr == null || ipStr.isEmpty()) throw new IOException("server ip is empty.");

        String host = ipStr;
        int port = 25565;

        int lastIndexOf = ipStr.lastIndexOf(":");
        if (lastIndexOf != -1) {
            String portArea = ipStr.substring(lastIndexOf + 1);
            try {
                int parsedPort = Integer.parseInt(portArea);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    host = ipStr.substring(0, lastIndexOf);
                    port = parsedPort;
                }
            } catch (Exception ignore) {
            }
        }
        return new ServerAddress(host, port);
    }

    public InetSocketAddress toInetSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    @Override
    public @NotNull String toString() {
        return host + ":" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServerAddress that = (ServerAddress) o;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
