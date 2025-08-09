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
package org.jackhuang.hmcl.upgrade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * @author Glavo
 */
public final class UpdateHandlerTest {

    private static void assertRename(String expected, String fileName, UpdateChannel channel, String version) {
        for (var ext : new String[]{".jar", ".exe", ".sh"}) {
            assertEquals(expected + ext, UpdateHandler.tryRename(fileName + ext, channel, version));
        }

        assertNull(UpdateHandler.tryRename(fileName, channel, version));
        assertNull(UpdateHandler.tryRename(fileName + ".unknown", channel, version));
    }

    @Test
    public void testRename() {
        assertRename("HMCL-999.999.999", "HMCL-3.6.15.287", UpdateChannel.STABLE, "999.999.999");
        assertRename("HMCL-999.999.999", "HMCL-3.6.15", UpdateChannel.STABLE, "999.999.999");
        assertRename("HMCL-999.999.999", "HMCL-3.6.dev-3873459", UpdateChannel.STABLE, "999.999.999");
        assertRename("HMCL-999.999.999", "HMCL-3.6.SNAPSHOT", UpdateChannel.STABLE, "999.999.999");
        assertRename("hmcl-stable-999.999.999", "hmcl-dev-3.6.15.287", UpdateChannel.STABLE, "999.999.999");
    }
}
