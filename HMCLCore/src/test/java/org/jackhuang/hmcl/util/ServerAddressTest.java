// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class ServerAddressTest {

    @Test
    public void testParse() {
        String raw6 = "[::1]:12345?key=value";
        ServerAddress ipv6 = ServerAddress.parse(raw6);
        assertEquals(raw6, ipv6.getRawAddress());
        assertEquals(Map.of("key", "value"), ipv6.getQueryProperties());

        String raw7 = "  example.com:12345  ";
        ServerAddress trimmed = ServerAddress.parse(raw7);
        assertEquals(new HostAndPort("example.com", 12345), trimmed.getHostAndPort());
        assertEquals(raw7, trimmed.getRawAddress());
    }

    @Test
    public void testParseInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:notaport"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:99999999999999999999"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1"));
    }

    @Test
    public void testGetters() {
        ServerAddress address = ServerAddress.parse("id@example.com:12345?key=value");
        assertEquals("id@example.com:12345?key=value", address.getRawAddress());
        assertEquals(new HostAndPort("example.com", 12345), address.getHostAndPort());
        assertEquals(Map.of("key", "value", "_id", "id"), address.getQueryProperties());
        assertTrue(address.hasQueryProperties());

        ServerAddress noQuery = ServerAddress.parse("example.com");
        assertTrue(noQuery.getQueryProperties().isEmpty());
        assertFalse(noQuery.hasQueryProperties());
    }
}
