// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.addon.resourcepack.ResourcePackManager.isMcVersionSupported;
import static org.jackhuang.hmcl.addon.resourcepack.ResourcePackManager.isMcVersionSupportsNewOptionsFormat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerTest {

    @Test
    void testIsMcVersionSupported() {
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("26.1-snapshot-1")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("25w14craftmine")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("1.21")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("1.16.5")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("1.13-pre3")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("17w48a")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("13w24a")));
        assertTrue(isMcVersionSupported(GameVersionNumber.asGameVersion("1.6.1")));

        assertFalse(isMcVersionSupported(GameVersionNumber.asGameVersion("13w23a")));
        assertFalse(isMcVersionSupported(GameVersionNumber.asGameVersion("1.6")));
        assertFalse(isMcVersionSupported(GameVersionNumber.asGameVersion("b1.1-1")));
    }

    @Test
    void testIsMcVersionSupportsNewOptionsFormat() {
        assertTrue(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("26.1.1")));
        assertTrue(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("25w14craftmine")));
        assertTrue(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("1.13")));
        assertTrue(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("17w43a")));

        assertFalse(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("1.12.2")));
        assertFalse(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("17w31a")));
        assertFalse(isMcVersionSupportsNewOptionsFormat(GameVersionNumber.asGameVersion("b1.1-1")));
    }
}
