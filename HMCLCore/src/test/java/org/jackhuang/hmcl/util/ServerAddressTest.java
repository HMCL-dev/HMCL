// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Glavo
 */
public final class ServerAddressTest {

    @Test
    public void testParse() {
        assertEquals(new ServerAddress("example.com"), ServerAddress.parse("example.com"));
        assertEquals(new ServerAddress("example.com", 25565), ServerAddress.parse("example.com:25565"));

        assertEquals(new ServerAddress("127.0.0.0"), ServerAddress.parse("127.0.0.0"));
        assertEquals(new ServerAddress("127.0.0.0", 0), ServerAddress.parse("127.0.0.0:0"));
        assertEquals(new ServerAddress("127.0.0.0", 12345), ServerAddress.parse("127.0.0.0:12345"));

        assertEquals(new ServerAddress("::1"), ServerAddress.parse("[::1]"));
        assertEquals(new ServerAddress("::1", 0), ServerAddress.parse("[::1]:0"));
        assertEquals(new ServerAddress("::1", 12345), ServerAddress.parse("[::1]:12345"));
        assertEquals(new ServerAddress("2001:db8::1"), ServerAddress.parse("[2001:db8::1]"));
        assertEquals(new ServerAddress("2001:db8::1", 0), ServerAddress.parse("[2001:db8::1]:0"));
        assertEquals(new ServerAddress("2001:db8::1", 12345), ServerAddress.parse("[2001:db8::1]:12345"));

        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("["));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[]]"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[]:0"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]:"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]|"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]|0"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]:a"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]:65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[::1]:-1"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[ ]:-1"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("[-]:-1"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:a"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:65536"));
        assertThrows(IllegalArgumentException.class, () -> ServerAddress.parse("example.com:-1"));
    }
}
