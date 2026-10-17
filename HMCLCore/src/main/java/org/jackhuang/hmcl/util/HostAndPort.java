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
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public record HostAndPort(
        @NotNull String host,
        int port
) {

    public HostAndPort {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid server address: " + port);
        }
    }

    public static @NotNull HostAndPort parseHostAndPort(@NotNull String input, int defaultPort) {
        String host;
        String portString;

        if (!input.startsWith("[")) {
            int colonPos = input.indexOf(':');
            if (colonPos >= 0 && input.indexOf(':', colonPos + 1) == -1) {
                host = input.substring(0, colonPos);
                portString = input.substring(colonPos + 1);
            } else {
                host = input;
                portString = null;
            }
        } else {
            int colonIndex = input.indexOf(':');
            int closeBracketIndex = input.lastIndexOf(']');
            if (!(colonIndex > -1 && closeBracketIndex > colonIndex)) {
                throw new IllegalArgumentException("Invalid server address: " + input);
            }

            if (closeBracketIndex + 1 == input.length()) {
                host = input.substring(1, closeBracketIndex);
                portString = null;
            } else {
                host = input.substring(1, closeBracketIndex);
                if (!(input.charAt(closeBracketIndex + 1) == ':')) {
                    throw new IllegalArgumentException("Invalid server address: " + input);
                }
                for (int i = closeBracketIndex + 2; i < input.length(); ++i) {
                    if (!Character.isDigit(input.charAt(i))) {
                        throw new IllegalArgumentException("Invalid server address: " + input);
                    }
                }
                portString = input.substring(closeBracketIndex + 2);
            }
        }

        int port;
        if (portString != null && !(portString = portString.trim()).isEmpty()) {
            try {
                port = Integer.parseInt(portString);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid server address: " + input, e);
            }
        } else {
            port = defaultPort;
        }
        if (host.trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid server address: " + input);
        }

        return new HostAndPort(host, port);
    }

    @NotNull InetSocketAddress createInetSocketAddress() throws UnknownHostException {
        return new InetSocketAddress(InetAddress.getByName(host), port);
    }

    public boolean equals(@NotNull String host, int port) {
        return host.equals(this.host) && port == this.port;
    }

    @Override
    public @NotNull String toString() {
        StringBuilder builder = new StringBuilder();

        if (host.indexOf(':') >= 0) {
            builder.append('[').append(host).append(']');
        } else {
            builder.append(host);
        }
        builder.append(':').append(port);

        return builder.toString();
    }

}
