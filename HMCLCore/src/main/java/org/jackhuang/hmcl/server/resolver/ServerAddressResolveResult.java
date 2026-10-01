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
package org.jackhuang.hmcl.server.resolver;

import org.jackhuang.hmcl.server.ServerStatusResult;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public sealed class ServerAddressResolveResult {

    private ServerAddressResolveResult() {

    }

    public static @NotNull ServerAddressResolveResult.FailureResult failed(FailureResult.Reason reason) {
        return new FailureResult(reason);
    }

    public static SuccessResult succeed(ServerAddress serverAddress, InetSocketAddress address) {
        Map<String, String> queryProperties = new LinkedHashMap<>(serverAddress.getQueryProperties());
        boolean isSame = serverAddress.getHostAndIp().equals(address.getHostName(), address.getPort());

        if (isSame && queryProperties.get("_o") != null) {
            queryProperties.put("_o", serverAddress.getHostAndIp().host() + ":" + serverAddress.getHostAndIp().port());
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

        if (queryArgBuilder.isEmpty()) {
            queryArgBuilder.insert(0, address.getHostName());
        } else {
            queryArgBuilder.insert(0, address.getHostName() + "?");
        }
        return new SuccessResult(address, queryArgBuilder.toString(), address.getPort());
    }

    public static final class FailureResult extends ServerAddressResolveResult {
        private final Reason reason;

        public FailureResult(Reason reason) {
            this.reason = reason;
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

        public SuccessResult(InetSocketAddress connectAddress, String packerHandshakeAddress, int packerHandshakePort) {
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

        @Override
        public String toString() {
            return "SuccessResult{" +
                    "connectAddress=" + connectAddress +
                    ", packerHandshakeAddress='" + packerHandshakeAddress + '\'' +
                    ", packerHandshakePort=" + packerHandshakePort +
                    '}';
        }
    }
}
