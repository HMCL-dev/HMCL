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
package org.jackhuang.hmcl.theme;

import javafx.scene.paint.Color;
import org.glavo.monetfx.*;
import org.jackhuang.hmcl.theme2.ThemeColor;

/// @author Glavo
public record ColorTheme(
        Color primaryColorSeed,
        Brightness brightness,
        ColorStyle colorStyle,
        Contrast contrast) {

    public static final ColorTheme DEFAULT = new ColorTheme(ThemeColor.DEFAULT.color(), Brightness.DEFAULT, ColorStyle.FIDELITY, Contrast.DEFAULT);

    public ColorScheme toColorScheme() {
        return ColorScheme.newBuilder()
                .setPrimaryColorSeed(primaryColorSeed)
                .setColorStyle(colorStyle)
                .setBrightness(brightness)
                .setSpecVersion(ColorSpecVersion.SPEC_2025)
                .setContrast(contrast)
                .build();
    }
}
