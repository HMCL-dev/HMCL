/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.resourcepack.ResourcePackManager.isMcVersionSupported;
import static org.jackhuang.hmcl.resourcepack.ResourcePackManager.isMcVersionSupportsNewOptionsFormat;
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
