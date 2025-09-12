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
package org.jackhuang.hmcl.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class DataSizeUnitTest {

    @Test
    public void testToString() {
        assertEquals("0 bytes", DataSizeUnit.format(0));
        assertEquals("1 byte", DataSizeUnit.format(1));
        assertEquals("2 bytes", DataSizeUnit.format(2));
        assertEquals("100 bytes", DataSizeUnit.format(100));
        assertEquals("1023 bytes", DataSizeUnit.format(1023));
        assertEquals("1 KiB", DataSizeUnit.format(1024));
        assertEquals("1 KiB", DataSizeUnit.format(1025));
        assertEquals("1.07 KiB", DataSizeUnit.format(1100));
        assertEquals("4 KiB", DataSizeUnit.format(4096));
        assertEquals("1 MiB", DataSizeUnit.format(1024 * 1024));
        assertEquals("1.5 MiB", DataSizeUnit.format((long) (1.5 * 1024 * 1024)));
        assertEquals("1 GiB", DataSizeUnit.format(1024 * 1024 * 1024));
        assertEquals("1.5 GiB", DataSizeUnit.format((long) (1.5 * 1024 * 1024 * 1024)));
        assertEquals("1 TiB", DataSizeUnit.format(1024L * 1024 * 1024 * 1024));
        assertEquals("1.5 TiB", DataSizeUnit.format((long) (1.5 * 1024 * 1024 * 1024 * 1024)));
    }

    @Test
    public void testConvertFromBytes() {
        assertEquals(1, DataSizeUnit.KILOBYTES.convertFromBytes(1024L));
        assertEquals(1.5, DataSizeUnit.KILOBYTES.convertFromBytes((long) (1024. * 1.5)));

        assertEquals(1, DataSizeUnit.MEGABYTES.convertFromBytes(1024L * 1024));
        assertEquals(1.5, DataSizeUnit.MEGABYTES.convertFromBytes((long) (1024 * 1024 * 1.5)));
    }

    @Test
    public void testConvertToBytes() {
        assertEquals(10., DataSizeUnit.BYTES.convertToBytes(10));
        assertEquals(10. * 1024, DataSizeUnit.KILOBYTES.convertToBytes(10));
        assertEquals(10. * 1024 * 1024, DataSizeUnit.MEGABYTES.convertToBytes(10));
        assertEquals(10. * 1024 * 1024 * 1024, DataSizeUnit.GIGABYTES.convertToBytes(10));
        assertEquals(10. * 1024 * 1024 * 1024 * 1024, DataSizeUnit.TERABYTES.convertToBytes(10));
    }

    private DataSizeUnitTest() {
    }
}
