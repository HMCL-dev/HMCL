/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
