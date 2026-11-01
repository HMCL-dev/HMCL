// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Glavo
 */
public final class ServerAddress {
    private static final char USER_CHARACTER = '@';
    private static final int DEFAULT_MINECRAFT_SERVER_PORT = 25565;

    private transient final @NotNull HostAndPort hostAndPort;
    private transient final @NotNull Map<String, String> queryProperties;

    // original address.
    private final @NotNull String rawAddress;

    private ServerAddress(
            @NotNull HostAndPort hostAndPort,
            @NotNull Map<String, String> queryProperties,
            @NotNull String rawAddress
    ) {
        this.hostAndPort = hostAndPort;
        this.queryProperties = Collections.unmodifiableMap(queryProperties);
        this.rawAddress = rawAddress;
    }

    /**
     * @throws IllegalArgumentException if the rawAddress is not a valid server address
     */
    public static @NotNull ServerAddress parse(@NotNull final String rawAddress) {
        String input = rawAddress;
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

            int atEnd = input.indexOf(USER_CHARACTER);
            if (atEnd != -1) {
                String value = input.substring(0, atEnd);
                queryProperties.put("_id", value);

                input = input.substring(atEnd + 1);
            }

            return new ServerAddress(HostAndPort.parse(input, DEFAULT_MINECRAFT_SERVER_PORT), queryProperties, rawAddress);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse URL " + rawAddress, e);
        }
    }

    public @NotNull String getRawAddress() {
        return rawAddress;
    }

    public @NotNull HostAndPort getHostAndPort() {
        return hostAndPort;
    }

    public @NotNull Map<String, String> getQueryProperties() {
        return queryProperties;
    }

    public boolean hasQueryProperties() {
        return !queryProperties.isEmpty();
    }

    @Override
    public String toString() {
        return "ServerAddress{" +
                "rawAddress='" + rawAddress + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ServerAddress that = (ServerAddress) o;
        return Objects.equals(rawAddress, that.rawAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(rawAddress);
    }
}
