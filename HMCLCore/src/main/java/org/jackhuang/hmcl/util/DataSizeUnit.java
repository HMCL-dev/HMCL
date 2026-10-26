// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
