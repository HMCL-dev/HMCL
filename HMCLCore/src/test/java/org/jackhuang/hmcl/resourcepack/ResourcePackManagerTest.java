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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackManagerTest {

    @Test
    void testIsMcVersionSupported() {
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("26.1-snapshot-1")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("25w14craftmine")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.21")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.16.5")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.13-pre3")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("17w48a")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("13w24a")));
        assertTrue(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.6.1")));

        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("13w23a")));
        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("1.6")));
        assertFalse(ResourcePackManager.isMcVersionSupported(GameVersionNumber.asGameVersion("b1.1-1")));
    }
}
