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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CSVTable {
    private final List<List<String>> table = new InfiniteSizeList<>();

    private CSVTable() {
    }

    public static CSVTable createEmpty() {
        return new CSVTable();
    }

    public String get(int x, int y) {
        List<String> row = table.get(y);
        if (row == null) {
            return null;
        }
        return row.get(x);
    }

    public void set(int x, int y, String txt) {
        List<String> row = table.get(y);
        if (row == null) {
            row = new InfiniteSizeList<>(x);
            table.set(y, row);
        }
        row.set(x, txt);
    }

    public void write(OutputStream outputStream) throws IOException {
        try (PrintWriter printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                outputStream, StandardCharsets.UTF_8)), false
        )) {
            for (List<String> row : this.table) {
                if (row != null) {
                    for (int j = 0; j < row.size(); j++) {
                        String txt = row.get(j);
                        if (txt != null) {
                            printWriter.write(this.escape(txt));
                        }

                        if (j != row.size() - 1) {
                            printWriter.write(',');
                        }
                    }
                }

                printWriter.write('\n');
            }
        }
    }

    private String escape(String txt) {
        if (!txt.contains("\"") && !txt.contains(",")) {
            return txt;
        } else {
            return "\"" + txt.replace("\"", "\"\"") + "\"";
        }
    }
}
