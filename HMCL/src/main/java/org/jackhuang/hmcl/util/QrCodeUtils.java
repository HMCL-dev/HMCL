// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import io.nayuki.qrcodegen.QrCode;

/// @author Glavo
public final class QrCodeUtils {
    public static String toSVGPath(QrCode qr) {
        return toSVGPath(qr, 0);
    }

    public static String toSVGPath(QrCode qr, int border) {
        int actualSize = qr.size + border * 2;

        var builder = new StringBuilder(qr.size * qr.size * 12);
        builder.append('M').append(actualSize).append(' ').append(actualSize).append("ZM0 0Z");

        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    if (x != 0 || y != 0)
                        builder.append(' ');
                    builder.append("M").append(x + border).append(',').append(y + border).append("h1v1h-1z");
                }
            }
        }
        return builder.toString();
    }

    private QrCodeUtils() {
    }
}
