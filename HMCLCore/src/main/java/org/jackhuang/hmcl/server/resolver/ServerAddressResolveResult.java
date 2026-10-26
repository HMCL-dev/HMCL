// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server.resolver;

import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract sealed class ServerAddressResolveResult {
    private final ServerAddress rawAddress;

    private ServerAddressResolveResult(ServerAddress rawAddress) {
        this.rawAddress = rawAddress;
    }

    public static @NotNull ServerAddressResolveResult.FailureResult failed(ServerAddress rawAddress, FailureResult.Reason reason, Exception exception) {
        return new FailureResult(rawAddress, reason, exception);
    }

    public static SuccessResult succeed(ServerAddress rawAddress, InetSocketAddress resolvedAddress) {
        Map<String, String> queryProperties = new LinkedHashMap<>(rawAddress.queryProperties());
        boolean isSame = rawAddress.hostAndPort().equals(resolvedAddress.getHostName(), resolvedAddress.getPort());

        if (!isSame || queryProperties.get("_o") != null) {
            queryProperties.put("_o", rawAddress.hostAndPort().host() + ":" + rawAddress.hostAndPort().port());
        }

        StringBuilder queryArgBuilder = new StringBuilder();
        boolean first = true;

        for (Map.Entry<String, String> entry : queryProperties.entrySet()) {
            String key = entry.getKey();
            if (!first) {
                queryArgBuilder.append("&");
            }
            first = false;

            queryArgBuilder.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            queryArgBuilder.append("=");
            queryArgBuilder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        // include port
        if (queryArgBuilder.isEmpty()) {
            queryArgBuilder.insert(0, resolvedAddress.getHostName());
        } else {
            queryArgBuilder.insert(0, resolvedAddress.getHostName() + "?");
        }
        return new SuccessResult(rawAddress, resolvedAddress, queryArgBuilder.toString(), resolvedAddress.getPort());
    }

    public ServerAddress getRawAddress() {
        return rawAddress;
    }

    public static final class FailureResult extends ServerAddressResolveResult {
        private final Reason reason;
        private final Exception exception;

        public FailureResult(ServerAddress rawAddress, Reason reason, Exception exception) {
            super(rawAddress);
            this.reason = reason;
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }

        public Reason getReason() {
            return reason;
        }

        public enum Reason {
            UNKNOWN_HOST,
            BLOCKED_BY_MOJANG;

            public ServerStatusResult.FailureResult.Reason getStatusReason() {
                return switch (this) {
                    case UNKNOWN_HOST -> ServerStatusResult.FailureResult.Reason.UNKNOWN_HOST;
                    case BLOCKED_BY_MOJANG -> ServerStatusResult.FailureResult.Reason.BLOCKED_BY_MOJANG;
                };
            }
        }
    }

    public static final class SuccessResult extends ServerAddressResolveResult {
        private final InetSocketAddress connectAddress;
        private final String packerHandshakeAddress;
        private final int packerHandshakePort;

        public SuccessResult(ServerAddress rawAddress, InetSocketAddress connectAddress, String packerHandshakeAddress, int packerHandshakePort) {
            super(rawAddress);
            this.connectAddress = connectAddress;
            this.packerHandshakeAddress = packerHandshakeAddress;
            this.packerHandshakePort = packerHandshakePort;
        }

        public InetSocketAddress getConnectAddress() {
            return connectAddress;
        }

        public String getPackerHandshakeAddress() {
            return packerHandshakeAddress;
        }

        public int getPackerHandshakePort() {
            return packerHandshakePort;
        }
    }
}
