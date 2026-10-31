// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Glavo
 */
public record ServerAddress(
        HostAndPort hostAndPort,
        Map<String, String> queryProperties
) {

    /**
     * @throws IllegalArgumentException if the raw is not a valid server address
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

                for (Pair<String, String> pair : NetworkUtils.parseQuery(queryPropertyStr, true)) {
                    queryProperties.put(pair.key(), pair.value() == null ? "" : pair.value());
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
        if (containQueryArgs) {
            if (!queryProperties.isEmpty()) {
                return NetworkUtils.withQuery(hostAndPort.toString(), queryProperties);
            }
        }
        return hostAndPort.toString();
    }
}
