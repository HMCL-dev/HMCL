// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class HostAndPortTest {

    @Test
    public void testParseHostAndPort() {
        assertEquals(new HostAndPort("example.com", 25565), HostAndPort.parse("example.com", 25565));
        assertEquals(new HostAndPort("example.com", 12345), HostAndPort.parse("example.com:12345", 25565));
        assertEquals(new HostAndPort("example.com", 25565), HostAndPort.parse("example.com:", 25565));
        assertEquals(new HostAndPort("127.0.0.1", 0), HostAndPort.parse("127.0.0.1:0", 25565));

        assertEquals(new HostAndPort("::1", 25565), HostAndPort.parse("[::1]", 25565));
        assertEquals(new HostAndPort("::1", 12345), HostAndPort.parse("[::1]:12345", 25565));
        assertEquals(new HostAndPort("2001:db8::1", 0), HostAndPort.parse("[2001:db8::1]:0", 25565));

        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("[", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("[]:0", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("[::1]|0", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("[::1]:a", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("[::1]:65536", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("example.com:a", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("example.com:-1", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("example.com:65536", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parse("   ", 25565));
    }

    @Test
    public void testToStringAndEquals() {
        HostAndPort hostAndPort = new HostAndPort("example.com", 25565);
        assertEquals("example.com:25565", hostAndPort.toString());
        assertEquals(new HostAndPort("example.com", 25565), hostAndPort);
        assertTrue(hostAndPort.equals("example.com", 25565));

        HostAndPort ipv6 = new HostAndPort("::1", 12345);
        assertEquals("[::1]:12345", ipv6.toString());
        assertTrue(ipv6.equals("::1", 12345));
    }
}
