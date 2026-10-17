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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class ServerAddressTest {

    @Test
    public void testParse() {
        ServerAddress address = ServerAddress.parse("override@example.com:12345?mode=survival&name=hello+world%21&_id=testId");
        assertEquals(new ServerAddress(new HostAndPort("example.com", 12345), Map.of(
                "mode", "survival",
                "name", "hello world!",
                "_id", "override"
        )), address);

        assertEquals(new ServerAddress(new HostAndPort("example.com", 25565), Map.of("key", "value", "empty", "")),
                ServerAddress.parse("example.com?key=value&empty"));
        assertEquals(new ServerAddress(new HostAndPort("example.com", 25565), Map.of("_id", "testId")),
                ServerAddress.parse("testId@example.com"));
        assertEquals(new ServerAddress(new HostAndPort("example.com", 25565), Map.of("k", "v", "x", "1")),
                ServerAddress.parse("example.com?x=1&k=v"));
    }

    @Test
    public void testToServerIp() {
        ServerAddress address = ServerAddress.parse("serverId@example.com:12345?mode=survival&name=hello+world%21");
        assertEquals("example.com:12345?mode=survival&name=hello+world%21&_id=serverId", address.toServerIp(true));
        assertEquals("example.com:12345", address.toServerIp(false));

        assertEquals("[::1]:25565?key=value", ServerAddress.parse("[::1]?key=value").toServerIp(true));
        assertEquals("[::1]:25565", ServerAddress.parse("[::1]").toServerIp(false));
    }
}
