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

import org.glavo.monetfx.*;

import java.util.*;

/// @author Glavo
public final class Theme {

    public static final Theme DEFAULT = new Theme(ThemeColor.DEFAULT, Brightness.DEFAULT, ColorStyle.FIDELITY, Contrast.DEFAULT);

    private final ThemeColor primaryColorSeed;
    private final Brightness brightness;
    private final ColorStyle colorStyle;
    private final Contrast contrast;

    public Theme(ThemeColor primaryColorSeed,
                 Brightness brightness,
                 ColorStyle colorStyle,
                 Contrast contrast
    ) {
        this.primaryColorSeed = primaryColorSeed;
        this.brightness = brightness;
        this.colorStyle = colorStyle;
        this.contrast = contrast;
    }

    public ColorScheme toColorScheme() {
        return ColorScheme.newBuilder()
                .setPrimaryColorSeed(primaryColorSeed.color())
                .setColorStyle(colorStyle)
                .setBrightness(brightness)
                .setSpecVersion(ColorSpecVersion.SPEC_2025)
                .setContrast(contrast)
                .build();
    }

    public ThemeColor primaryColorSeed() {
        return primaryColorSeed;
    }

    public Brightness brightness() {
        return brightness;
    }

    public ColorStyle colorStyle() {
        return colorStyle;
    }

    public Contrast contrast() {
        return contrast;
    }

    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof Theme that
                && this.primaryColorSeed.color().equals(that.primaryColorSeed.color())
                && this.brightness.equals(that.brightness)
                && this.colorStyle.equals(that.colorStyle)
                && this.contrast.equals(that.contrast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(primaryColorSeed, brightness, colorStyle, contrast);
    }

    @Override
    public String toString() {
        return "Theme[" +
                "primaryColorSeed=" + primaryColorSeed + ", " +
                "brightness=" + brightness + ", " +
                "colorStyle=" + colorStyle + ", " +
                "contrast=" + contrast + ']';
    }

}
