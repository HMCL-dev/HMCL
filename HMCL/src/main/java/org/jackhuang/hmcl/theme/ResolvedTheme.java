// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
