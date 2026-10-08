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

import org.jackhuang.hmcl.server.ServerBlockedByMojangException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServerAddress {
    private final String rawServerIp;
    private final HostAndPort hostAndPort;
    private final Map<String, String> queryProperties;

    private ServerAddress(
            String rawServerIp,
            HostAndPort hostAndPort,
            Map<String, String> queryProperties
    ) {
        this.rawServerIp = rawServerIp;
        this.hostAndPort = hostAndPort;
        this.queryProperties = queryProperties;
    }

    public static ServerAddress fromString(String input) throws IOException {
        String rawServerIp = input;
        if (input == null || input.isEmpty()) throw new IOException("server ip is empty.");


        Map<String, String> queryProperties = new LinkedHashMap<>();

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

        int atEnd = input.lastIndexOf('@');
        if (atEnd != -1) {
            queryProperties.put("_id", input.substring(0, atEnd));
            input = input.substring(atEnd + 1);
        }

        String host = input;
        int port = 25565;

        int lastIndexOf = input.lastIndexOf(":");
        if (lastIndexOf != -1) {
            String portArea = input.substring(lastIndexOf + 1);
            try {
                int parsedPort = Integer.parseInt(portArea);
                if (parsedPort > 0 && parsedPort <= 65535) {
                    host = input.substring(0, lastIndexOf);
                    port = parsedPort;
                }
            } catch (Exception ignore) {
            }
        }
        return new ServerAddress(rawServerIp, new HostAndPort(host, port), queryProperties);
    }

    public ServerAddressResolveResult resolve() {
        try {
            InetSocketAddress inetSocketAddress = hostAndPort.toInetSocketAddress();
            if (MojangBlockServerChecker.isBlocking(inetSocketAddress)) {
                return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.BLOCKED_BY_MOJANG, new ServerBlockedByMojangException("blocked by mojang block server"));
            }
            HostAndPort lookupDnsResult = ServerDnsSrvRedirector.lookup(this);
            if (lookupDnsResult != null) {
                inetSocketAddress = lookupDnsResult.toInetSocketAddress();
                if (MojangBlockServerChecker.isBlocking(inetSocketAddress)) {
                    return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.BLOCKED_BY_MOJANG, new ServerBlockedByMojangException("blocked by mojang block server"));
                }
            }
            return ServerAddressResolveResult.succeed(this, inetSocketAddress);
        } catch (UnknownHostException e) {
            return ServerAddressResolveResult.failed(this, ServerAddressResolveResult.FailureResult.Reason.UNKNOWN_HOST, e);
        }
    }

    public Map<String, String> getQueryProperties() {
        return queryProperties;
    }

    public HostAndPort getHostAndPort() {
        return hostAndPort;
    }

    public String getRawServerIp() {
        return rawServerIp;
    }
}
