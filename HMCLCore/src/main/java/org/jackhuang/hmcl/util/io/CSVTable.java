/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.InfiniteSizeList;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CSVTable {
    private int columnCount = 0;
    private int rowCount = 0;
    private final InfiniteSizeList<InfiniteSizeList<String>> table = new InfiniteSizeList<>();

    public CSVTable() {
    }

    public int getColumnCount() {
        return columnCount;
    }

    public int getRowCount() {
        return rowCount;
    }

    public @NotNull String get(int x, int y) {
        List<String> row = table.get(y);
        if (row == null) {
            return "";
        }
        String value = row.get(x);
        return value != null ? value : "";
    }

    public void set(int x, int y, String txt) {
        if (x < 0 || y < 0)
            throw new IllegalArgumentException("x or y must be greater than or equal to 0");

        InfiniteSizeList<String> row = table.get(y);
        if (row == null) {
            row = new InfiniteSizeList<>(x);
            table.set(y, row);
        }
        row.set(x, txt);

        columnCount = Integer.max(columnCount, x + 1);
        rowCount = Integer.max(rowCount, y + 1);
    }

    public void write(Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            write(writer);
        }
    }

    public void write(Appendable out) throws IOException {
        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            List<String> row = this.table.get(rowIndex);
            if (row != null) {
                for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                    String txt = row.get(columnIndex);
                    if (txt != null) {
                        if (txt.indexOf('"') < 0 && txt.indexOf(',') < 0 && txt.indexOf('\n') < 0 && txt.indexOf('\r') < 0)
                            out.append(txt);
                        else {
                            out.append('"');
                            for (int i = 0; i < txt.length(); i++) {
                                char c = txt.charAt(i);
                                switch (c) {
                                    case '"':
                                        out.append("\\\"");
                                        break;
                                    case '\r':
                                        out.append("\\r");
                                        break;
                                    case '\n':
                                        out.append("\\n");
                                        break;
                                    default:
                                        out.append(c);
                                        break;
                                }
                            }
                            out.append('"');
                        }
                    }

                    if (columnIndex < columnCount - 1)
                        out.append(',');
                }
            } else {
                for (int columnIndex = 0; columnIndex < columnCount - 1; columnIndex++) {
                    out.append(',');
                }
            }

            out.append('\n');
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        try {
            write(builder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return builder.toString();
    }
}
