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
