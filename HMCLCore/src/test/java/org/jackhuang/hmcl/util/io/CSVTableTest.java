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
package org.jackhuang.hmcl.util.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class CSVTableTest {

    @Test
    public void testCreate() {
        CSVTable table = new CSVTable();

        assertEquals(0, table.getColumnCount());
        assertEquals(0, table.getRowCount());

        table.set(0, 0, "test1");
        assertEquals(1, table.getColumnCount());
        assertEquals(1, table.getRowCount());

        table.set(4, 0, "test2");
        assertEquals(5, table.getColumnCount());
        assertEquals(1, table.getRowCount());

        table.set(2, 1, "test3");
        assertEquals(5, table.getColumnCount());
        assertEquals(2, table.getRowCount());

        assertEquals("test1", table.get(0, 0));
        assertEquals("", table.get(1, 0));
        assertEquals("", table.get(0, 1));
        assertEquals("test2", table.get(4, 0));
        assertEquals("test3", table.get(2, 1));
    }

    @Test
    public void testToString() {
        CSVTable table = new CSVTable();
        table.set(0, 0, "a");
        table.set(1, 0, "b");
        table.set(3, 0, "c");
        table.set(0, 2, "a,b");
        table.set(1, 2, "c");
        table.set(3, 2, "d\"e\n");

        assertEquals("a,b,,c\n,,,\n\"a,b\",c,,\"d\\\"e\\n\"\n", table.toString());

    }
}
