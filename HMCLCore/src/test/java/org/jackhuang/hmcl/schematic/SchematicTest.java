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
package org.jackhuang.hmcl.schematic;

import org.jackhuang.hmcl.game.CrashReportAnalyzerTest;
import org.jackhuang.hmcl.util.Vec3i;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class SchematicTest {
    private static Schematic load(String name) throws IOException, URISyntaxException {
        URL resource = CrashReportAnalyzerTest.class.getResource(name);
        if (resource == null)
            throw new IOException("Resource not found: " + name);
        return Schematic.load(Paths.get(resource.toURI()));
    }

    @Test
    public void test() throws Exception {
        {
            LitematicFile lFile = (LitematicFile) load("/schematics/test.litematic");
            assertEquals(SchematicType.LITEMATIC, lFile.getType());
            assertEquals("刷石机一桶岩浆下推爆破8.3万每小时", lFile.getName());
            assertEquals("hsds", lFile.getAuthor());
            assertEquals("", lFile.getDescription());
            assertEquals(Instant.ofEpochMilli(1746443586433L), lFile.getTimeCreated());
            assertEquals(Instant.ofEpochMilli(1746443586433L), lFile.getTimeModified());
            assertEquals(1334, lFile.getTotalBlocks().orElse(0));
            assertEquals(5746, lFile.getTotalVolume().orElse(0));
            assertEquals(new Vec3i(17, 26, 13), lFile.getEnclosingSize());
            assertEquals(1, lFile.getRegionCount().orElse(0));
            assertEquals(4325, lFile.getMinecraftDataVersion().orElse(0));
            assertEquals("4325", lFile.getMinecraftVersion());
            assertEquals(7, lFile.getVersion().orElse(0));
            assertEquals(1, lFile.getSubVersion().orElse(-1));
        }

        {
            SchemFile sFile = (SchemFile) load("/schematics/test.schematic");
            assertEquals(SchematicType.SCHEM, sFile.getType());
            assertEquals("test", sFile.getName());
            assertEquals(new Vec3i(28, 35, 18), sFile.getEnclosingSize());
            assertEquals(17640, sFile.getTotalVolume().orElse(0));
            assertEquals("Alpha", sFile.getMinecraftVersion());
        }

        {
            SchemFile sFileSponge = (SchemFile) load("/schematics/test.schem");
            assertEquals(SchematicType.SCHEM, sFileSponge.getType());
            assertEquals("test", sFileSponge.getName());
            assertEquals(3465, sFileSponge.getMinecraftDataVersion().orElse(0));
            assertEquals("3465", sFileSponge.getMinecraftVersion());
            assertEquals(new Vec3i(9, 5, 9), sFileSponge.getEnclosingSize());
        }

        {
            NBTStructureFile nFile = (NBTStructureFile) load("/schematics/test.nbt");
            assertEquals(SchematicType.NBT_STRUCTURE, nFile.getType());
            assertEquals("test", nFile.getName());
            assertEquals(new Vec3i(9, 11, 13), nFile.getEnclosingSize());
            assertEquals(1287, nFile.getTotalVolume().orElse(0));
            assertEquals(3465, nFile.getMinecraftDataVersion().orElse(0));
            assertEquals("3465", nFile.getMinecraftVersion());
        }
    }
}
