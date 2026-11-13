// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.server;

import org.glavo.nbt.tag.StringTag;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {

    @Test
    void testCreate() {
        Server s = Server.createServer("name", "ip");
        assertEquals("name", s.getName());
        assertEquals("ip", s.getIp());
        assertNull(s.getIcon());
        assertEquals(Server.ServerPackStatus.PROMPT, s.getServerPackStatus());


        assertEquals("name", ((StringTag) Objects.requireNonNull(s.serverTag.get("name"))).getValue());
        assertEquals("ip", ((StringTag) Objects.requireNonNull(s.serverTag.get("ip"))).getValue());
        assertNull(s.serverTag.get("icon"));
        assertNull(s.serverTag.get("acceptTextures"));
    }

    @Test
    void testWith() {
        Server original = Server.createServer("n", "ip1");
        Server rehosted = original.withIp("ip2");

        assertNotSame(original, rehosted);
        assertEquals("ip2", rehosted.getIp());
        assertEquals("ip1", original.getIp());
    }

    @Test
    void testChain() {
        Server original = Server.createServer("a", "ip1");

        Server result = original
                .withName("b")
                .withIp("ip2")
                .withIcon("icon")
                .withServerPackStatus(Server.ServerPackStatus.ENABLED);

        assertEquals("a", original.getName());
        assertEquals("ip1", original.getIp());
        assertNull(original.getIcon());
        assertEquals(Server.ServerPackStatus.PROMPT, original.getServerPackStatus());

        assertEquals("b", result.getName());
        assertEquals("ip2", result.getIp());
        assertEquals("icon", result.getIcon());
        assertEquals(Server.ServerPackStatus.ENABLED, result.getServerPackStatus());
    }
}
