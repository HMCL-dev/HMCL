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

import javafx.geometry.Point3D;
import org.jackhuang.hmcl.game.CrashReportAnalyzerTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class LitematicFileTest {
    private static LitematicFile load(String name) throws IOException, URISyntaxException {
        URL resource = CrashReportAnalyzerTest.class.getResource(name);
        if (resource == null)
            throw new IOException("Resource not found: " + name);
        return LitematicFile.load(Paths.get(resource.toURI()));
    }

    @Test
    public void test() throws Exception {
        LitematicFile file = load("/schematics/test.litematic");
        assertEquals("刷石机一桶岩浆下推爆破8.3万每小时", file.getName());
        assertEquals("hsds", file.getAuthor());
        assertEquals("", file.getDescription());
        assertEquals(Instant.ofEpochMilli(1746443586433L), file.getTimeCreated());
        assertEquals(Instant.ofEpochMilli(1746443586433L), file.getTimeModified());
        assertEquals(1334, file.getTotalBlocks());
        assertEquals(5746, file.getTotalVolume());
        assertEquals(new Point3D(17, 26, 13), file.getEnclosingSize());
        assertEquals(1, file.getRegionCount());
    }
}
