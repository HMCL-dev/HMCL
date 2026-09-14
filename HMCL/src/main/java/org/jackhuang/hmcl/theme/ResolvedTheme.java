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
import org.jetbrains.annotations.NotNullByDefault;

import java.util.*;

/// Concrete launcher theme values resolved for MonetFX.
///
/// @param primaryColorSeed Color seed used to generate the MonetFX color scheme.
/// @param brightness       Brightness used by the generated color scheme.
/// @param colorStyle       MonetFX color style used by the generated color scheme.
/// @param contrast         MonetFX contrast level used by the generated color scheme.
@NotNullByDefault
public record ResolvedTheme(ThemeColor primaryColorSeed,
                            Brightness brightness,
                            ColorStyle colorStyle,
                            Contrast contrast) {

    /// Default launcher theme used when no custom theme values are configured.
    public static final ResolvedTheme DEFAULT = new ResolvedTheme(ThemeColor.DEFAULT, Brightness.DEFAULT, ColorStyle.FIDELITY, Contrast.DEFAULT);

    /// Converts these resolved values to a MonetFX color scheme.
    ///
    /// @return the generated color scheme
    public ColorScheme toColorScheme() {
        return ColorScheme.newBuilder()
                .setPrimaryColorSeed(primaryColorSeed.color())
                .setColorStyle(colorStyle)
                .setBrightness(brightness)
                .setSpecVersion(ColorSpecVersion.SPEC_2025)
                .setContrast(contrast)
                .build();
    }

}
