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
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class HostAndPortTest {

    @Test
    public void testParseHostAndPort() {
        assertEquals(new HostAndPort("example.com", 25565), HostAndPort.parseHostAndPort("example.com", 25565));
        assertEquals(new HostAndPort("example.com", 12345), HostAndPort.parseHostAndPort("example.com:12345", 25565));
        assertEquals(new HostAndPort("example.com", 25565), HostAndPort.parseHostAndPort("example.com:", 25565));
        assertEquals(new HostAndPort("127.0.0.1", 0), HostAndPort.parseHostAndPort("127.0.0.1:0", 25565));

        assertEquals(new HostAndPort("::1", 25565), HostAndPort.parseHostAndPort("[::1]", 25565));
        assertEquals(new HostAndPort("::1", 12345), HostAndPort.parseHostAndPort("[::1]:12345", 25565));
        assertEquals(new HostAndPort("2001:db8::1", 0), HostAndPort.parseHostAndPort("[2001:db8::1]:0", 25565));

        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("[", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("[]:0", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("[::1]|0", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("[::1]:a", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("[::1]:65536", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("example.com:a", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("example.com:-1", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("example.com:65536", 25565));
        assertThrows(IllegalArgumentException.class, () -> HostAndPort.parseHostAndPort("   ", 25565));
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
