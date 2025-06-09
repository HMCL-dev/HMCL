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

import java.text.DecimalFormat;

/**
 * @author Glavo
 */
public enum DataSizeUnit {
    BYTES,
    KILOBYTES,
    MEGABYTES,
    GIGABYTES,
    TERABYTES;

    private static final DataSizeUnit[] VALUES = values();
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##");

    public static String format(long bytes) {
        for (int i = VALUES.length - 1; i > 0; i--) {
            DataSizeUnit unit = VALUES[i];
            if (bytes >= unit.bytes) {
                return unit.formatBytes(bytes);
            }
        }

        return bytes == 1 ? "1 byte" : bytes + " bytes";
    }

    private final long bytes = 1L << (ordinal() * 10);

    private final char abbreviationChar = name().charAt(0);
    private final String abbreviation = abbreviationChar == 'B' ? "B" : abbreviationChar + "iB";

    public double convertFromBytes(long bytes) {
        return (double) bytes / this.bytes;
    }

    public long convertToBytes(double amount) {
        return (long) (amount * this.bytes);
    }

    private String format(double amount) {
        return FORMAT.format(amount) + " " + this.abbreviation;
    }

    public String formatBytes(long bytes) {
        return format(convertFromBytes(bytes));
    }
}
