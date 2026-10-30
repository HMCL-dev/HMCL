// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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

        ServerAddress badAddress = ServerAddress.parse("overwrite@example.com:12345?_id=serverId&badArg1&badArg2&badArg3=badValue1=badValue2=badValue3&=&&&");
        assertEquals(new ServerAddress(new HostAndPort("example.com", 12345), Map.of(
                "_id", "overwrite",
                "badArg1", "",
                "badArg2", "",
                "badArg3", "badValue1=badValue2=badValue3",
                "", ""
        )), badAddress);
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
