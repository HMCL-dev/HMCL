// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.theme;

import org.glavo.monetfx.Brightness;
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for theme-pack manifest parsing and resolution behavior.
@NotNullByDefault
public final class ThemePackManifestTest {
    /// Invalid optional metadata fields are ignored instead of rejecting the whole manifest.
    @Test
    public void invalidOptionalManifestFieldsAreIgnored() {
        ThemePackManifest manifest = Objects.requireNonNull(JsonUtils.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.invalid-optionals",
                  "version": "1.0.0",
                  "name": "Example",
                  "authors": "invalid",
                  "description": "",
                  "icon": "../bad.png",
                  "theme": {
                    "id": "../bad",
                    "name": "",
                    "description": [],
                    "icon": "../theme.png"
                  }
                }
                """, ThemePackManifest.class));

        assertTrue(manifest.authors().isEmpty());
        assertNull(manifest.description());
        assertNull(manifest.icon());

        Theme theme = manifest.themes().get(0);
        assertNull(theme.id());
        assertNull(theme.name());
        assertNull(theme.description());
        assertNull(theme.icon());
    }

    /// Applying a theme without a background should resolve to the launcher default background.
    @Test
    public void emptyThemeUsesDefaultBackgroundAfterApplying() throws Exception {
        ThemePackManifest manifest = Objects.requireNonNull(JsonUtils.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.empty-theme",
                  "version": "1.0.0",
                  "name": "Example",
                  "theme": {}
                }
                """, ThemePackManifest.class));
        Theme theme = manifest.themes().get(0);

        ThemePackManager.ResolvedBackground background = ThemePackManager.resolveBackgroundAfterApplyingTheme(
                new ThemePackManager.InstalledThemePack(Path.of("example.hmcl-theme"), manifest),
                theme,
                new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

        assertEquals(BackgroundType.DEFAULT, background.type());
        assertEquals(1.0, background.opacity(), 0.0);
        assertNull(theme.appearance().windowTransparent());
    }

}
