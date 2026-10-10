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

import org.jackhuang.hmcl.server.ServerBlockedByMojangException;
import org.jackhuang.hmcl.server.resolver.MojangBlockServerChecker;
import org.jackhuang.hmcl.server.resolver.ServerAddressResolveResult;
import org.jackhuang.hmcl.server.resolver.ServerDnsSrvRedirector;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Glavo
 */
public record ServerAddress(
        HostAndPort hostAndPort,
        Map<String, String> queryProperties
) {

    private static final int DEFAULT_SERVER_PORT = 25565;

    public ServerAddress(@NotNull String host) {
        this(host, DEFAULT_SERVER_PORT);
    }

    private static IllegalArgumentException illegalAddress(String address) {
        return new IllegalArgumentException("Invalid server address: " + address);
    }

    public ServerAddress(@NotNull String host, int port) {
        this(new HostAndPort(host, port), new LinkedHashMap<>());
    }

    /**
     * @throws IllegalArgumentException if the input is not a valid server address
     */
    public static @NotNull ServerAddress parse(@NotNull final String raw) {
        String input = raw;
        try {
            input = input.trim();

            Map<String, String> queryProperties = new LinkedHashMap<>();

            // parse query properties
            int queryStart = input.lastIndexOf('?');
            if (queryStart != -1) {
                String queryPropertyStr = input.substring(queryStart + 1);
                String[] args = queryPropertyStr.split("&");
                for (String arg : args) {
                    int separator = arg.indexOf('=');

                    String key;
                    String value;
                    if (separator >= 0) {
                        key = URLDecoder.decode(arg.substring(0, separator), StandardCharsets.UTF_8);
                        value = URLDecoder.decode(arg.substring(separator + 1), StandardCharsets.UTF_8);
                    } else {
                        key = URLDecoder.decode(arg, StandardCharsets.UTF_8);
                        value = "";
                    }
                    queryProperties.put(key, value);
                }
                input = input.substring(0, queryStart);
            }
            int atEnd = input.indexOf('@');
            if (atEnd != -1) {
                queryProperties.put("_id", input.substring(0, atEnd));
                input = input.substring(atEnd + 1);
            }

            return new ServerAddress(HostAndPort.parseHostAndPort(input, 25565), queryProperties);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse URL " + raw, e);
        }
    }

    public ServerAddressResolveResult resolve() {
        try {
            InetSocketAddress inetSocketAddress = hostAndPort.createInetSocketAddress();
            if (MojangBlockServerChecker.isBlocking(inetSocketAddress)) {
                return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.BLOCKED_BY_MOJANG, new ServerBlockedByMojangException("blocked by mojang block server"));
            }
            HostAndPort lookupDnsResult = ServerDnsSrvRedirector.lookup(this);
            if (lookupDnsResult != null) {
                inetSocketAddress = lookupDnsResult.createInetSocketAddress();
                if (MojangBlockServerChecker.isBlocking(inetSocketAddress)) {
                    return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.BLOCKED_BY_MOJANG, new ServerBlockedByMojangException("blocked by mojang block server"));
                }
            }
            return ServerAddressResolveResult.succeed(this, inetSocketAddress);
        } catch (UnknownHostException e) {
            return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.UNKNOWN_HOST, e);
        }
    }

    public String toServerIp(boolean containQueryArgs) {
        StringBuilder sb = new StringBuilder(hostAndPort.toString());
        if (containQueryArgs) {
            if (!queryProperties.isEmpty()) {
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
                sb.append("?");
                sb.append(queryArgBuilder);
            }
        }
        return sb.toString();
    }
}
