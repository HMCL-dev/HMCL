// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.Objects;

public final class HostAndPort {
    private final @NotNull String host;
    private final int port;

    @VisibleForTesting
    HostAndPort(@NotNull String host, int port) {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Invalid server address: " + port);
        }
        this.host = host;
        this.port = port;
    }

    public static @NotNull HostAndPort parse(@NotNull String input, int defaultPort) {
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

    public @NotNull String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (HostAndPort) obj;
        return Objects.equals(this.host, that.host) &&
                this.port == that.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }
}
