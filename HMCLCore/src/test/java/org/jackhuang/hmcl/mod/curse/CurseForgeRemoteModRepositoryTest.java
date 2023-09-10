/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod.curse;

import org.jackhuang.hmcl.util.MurmurHash2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class CurseForgeRemoteModRepositoryTest {

    @Test
    @Disabled
    public void testMurmurHash() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = Files.newInputStream(Paths.get("C:\\Users\\huang\\Downloads\\JustEnoughCalculation-1.16.5-3.8.5.jar"))) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = is.read(buf, 0, buf.length)) > 0) {
                for (int i = 0; i < len; i++) {
                    byte b = buf[i];
                    if (b != 9 && b != 10 && b != 13 && b != 32) {
                        baos.write(b);
                    }
                }
            }

        }
        long hash = Integer.toUnsignedLong(MurmurHash2.hash32(baos.toByteArray(), baos.size(), 1));

        assertEquals(hash, 3333498611L);
    }
}
