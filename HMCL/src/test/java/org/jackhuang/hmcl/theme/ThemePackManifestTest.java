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
