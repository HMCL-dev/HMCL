// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNull;

import java.net.URLDecoder;
import java.net.URLEncoder;
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
