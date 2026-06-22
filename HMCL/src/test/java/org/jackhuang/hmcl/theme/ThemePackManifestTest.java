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

import com.google.gson.JsonParseException;
import org.glavo.monetfx.Brightness;
import org.glavo.monetfx.ColorStyle;
import org.glavo.monetfx.Contrast;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests for theme-pack manifest parsing and conditional override resolution.
@NotNullByDefault
public final class ThemePackManifestTest {

    /// A representative theme-pack manifest containing default appearance fields and conditional overrides.
    private static final String MANIFEST = """
            {
              "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
              "id": "example.nature",
              "version": "1.0.0",
              "name": "Nature",
              "authors": ["Example"],
              "theme": {
                "id": "forest",
                "name": "Forest",
                "thumbnail": "assets/thumbnails/forest.png",
                "color": "#4D7C3A",
                "brightness": "adaptive",
                "colorStyle": "fidelity",
                "contrast": "default",
                "background": {
                  "type": "image",
                  "path": "assets/wallpapers/forest.webp",
                  "opacity": 0.8
                },
                "overrides": [
                  {
                    "condition": {
                      "brightness": "dark"
                    },
                    "color": "#6FA65A",
                    "background": {
                      "path": "assets/wallpapers/forest-dark.webp"
                    }
                  },
                  {
                    "condition": {
                      "brightness": "dark",
                      "os": "windows"
                    },
                    "contrast": "high",
                    "background": {
                      "opacity": 0.9
                    }
                  },
                  {
                    "condition": {
                      "language": ["zh", "ja"]
                    },
                    "colorStyle": "tonal_spot"
                  }
                ]
              }
            }
            """;

    /// Tests that matching overrides are applied in declaration order and deeply merge backgrounds.
    @Test
    public void testResolveMatchingOverrides() {
        Theme theme = parseForestTheme();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.DARK, "windows", "en");

        ThemeAppearance appearance = theme.resolve(context);
        ResolvedTheme resolvedTheme = appearance.toResolvedTheme(context);
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#6FA65A"), appearance.color().resolveFallback());
        assertEquals(Brightness.DARK, resolvedTheme.brightness());
        assertEquals(ColorStyle.FIDELITY, resolvedTheme.colorStyle());
        assertEquals(Contrast.HIGH, resolvedTheme.contrast());
        assertEquals(ThemeBackground.Type.IMAGE, background.effectiveType());
        assertEquals("assets/wallpapers/forest-dark.webp", background.path());
        assertEquals(0.9, background.opacity());
    }

    /// Tests that array condition values match any listed value.
    @Test
    public void testConditionArrayMatchesAnyValue() {
        Theme theme = parseForestTheme();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "zh");

        ResolvedTheme resolvedTheme = theme.toResolvedTheme(context);

        assertEquals(Brightness.LIGHT, resolvedTheme.brightness());
        assertEquals(ColorStyle.TONAL_SPOT, resolvedTheme.colorStyle());
        assertEquals(Contrast.DEFAULT, resolvedTheme.contrast());
    }

    /// Tests that non-matching overrides leave default fields unchanged.
    @Test
    public void testNonMatchingOverridesAreIgnored() {
        Theme theme = parseForestTheme();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "en");

        ThemeAppearance appearance = theme.resolve(context);
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColorSource.Type.CUSTOM, appearance.color().type());
        assertEquals(ThemeColor.of("#4D7C3A"), appearance.color().resolveFallback());
        assertEquals(ColorStyle.FIDELITY, appearance.colorStyle());
        assertEquals(Contrast.DEFAULT, appearance.contrast());
        assertEquals("assets/wallpapers/forest.webp", background.path());
        assertEquals(0.8, background.opacity());
    }

    /// Tests that an empty condition matches every resolution context.
    @Test
    public void testEmptyConditionMatchesEveryContext() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.empty-condition",
                  "version": "1.0.0",
                  "name": "Empty Condition",
                  "theme": {
                    "id": "current",
                    "name": "Current",
                    "color": "#111111",
                    "overrides": [
                      {
                        "condition": {},
                        "color": "#222222"
                      }
                    ]
                  }
                }
                """);
        Theme theme = manifest.findTheme("current");
        assertNotNull(theme);

        ThemeAppearance appearance = theme.resolve(
                new ThemeResolveContext(Brightness.LIGHT, "linux", "en"));

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#222222"), appearance.color().resolveFallback());
    }

    /// Tests that a single-theme manifest can omit the theme ID and name.
    @Test
    public void testSingleThemeCanOmitThemeIdAndName() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.single",
                  "version": "1.0.0",
                  "name": "Single",
                  "theme": {
                    "color": "#111111"
                  }
                }
                """);

        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        assertNull(theme.id());
        assertNull(theme.name());
    }

    /// Tests that multi-theme manifests must provide explicit theme IDs.
    @Test
    public void testMultiThemeRequiresThemeIds() {
        assertThrows(JsonParseException.class, () -> ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.multi",
                  "version": "1.0.0",
                  "name": "Multi",
                  "themes": [
                    {
                      "name": "First",
                      "color": "#111111"
                    },
                    {
                      "id": "second",
                      "name": "Second",
                      "color": "#222222"
                    }
                  ]
                }
                """));
    }

    /// Tests that multi-theme manifests must provide explicit theme names.
    @Test
    public void testMultiThemeRequiresThemeNames() {
        assertThrows(JsonParseException.class, () -> ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.multi",
                  "version": "1.0.0",
                  "name": "Multi",
                  "themes": [
                    {
                      "id": "first",
                      "color": "#111111"
                    },
                    {
                      "id": "second",
                      "name": "Second",
                      "color": "#222222"
                    }
                  ]
                }
                """));
    }

    /// Tests that the `themes` array can also represent a single-theme manifest.
    @Test
    public void testThemesArrayCanContainOneTheme() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.single-array",
                  "version": "1.0.0",
                  "name": "Single Array",
                  "themes": [
                    {
                      "id": "single",
                      "name": "Single",
                      "color": "#111111"
                    }
                  ]
                }
                """);

        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        assertEquals("single", theme.id());
        assertEquals("Single", theme.name());
    }

    /// Tests that `themes` array entries must provide explicit theme identities even with one entry.
    @Test
    public void testThemesArrayRequiresThemeIdentityForSingleEntry() {
        assertThrows(JsonParseException.class, () -> ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.single-array",
                  "version": "1.0.0",
                  "name": "Single Array",
                  "themes": [
                    {
                      "color": "#111111"
                    }
                  ]
                }
                """));
    }

    /// Tests that single-theme and multi-theme declarations are mutually exclusive.
    @Test
    public void testRejectBothThemeAndThemes() {
        assertThrows(JsonParseException.class, () -> ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.both",
                  "version": "1.0.0",
                  "name": "Both",
                  "theme": {
                    "color": "#111111"
                  },
                  "themes": [
                    {
                      "id": "first",
                      "name": "First",
                      "color": "#111111"
                    },
                    {
                      "id": "second",
                      "name": "Second",
                      "color": "#222222"
                    }
                  ]
                }
                """));
    }

    /// Tests that additional fields are ignored for forward compatibility.
    @Test
    public void testIgnoreAdditionalFields() {
        String json = """
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.additional-fields",
                  "version": "1.0.0",
                  "name": "Additional Fields",
                  "futureManifestField": true,
                  "theme": {
                    "futureThemeField": "ignored",
                    "color": {
                      "source": "wallpaper",
                      "fallback": "#4D7C3A",
                      "futureColorField": "ignored"
                    },
                    "background": {
                      "type": "image",
                      "path": "assets/wallpapers/forest.webp",
                      "futureBackgroundField": "ignored"
                    },
                    "overrides": [
                      {
                        "condition": {
                          "brightness": "dark"
                        },
                        "futureOverrideField": "ignored",
                        "color": "#6FA65A"
                      }
                    ]
                  }
                }
                """;

        ThemePackManifest manifest = ThemePackManifest.fromJson(json);
        assertEquals("example.additional-fields", manifest.id());

        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);

        ThemeAppearance appearance = theme.resolve(
                new ThemeResolveContext(Brightness.DARK, "windows", "en"));
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#6FA65A"), appearance.color().resolveFallback());
        assertEquals("assets/wallpapers/forest.webp", background.path());
    }

    /// Tests that unknown condition keys are accepted but do not match the current context.
    @Test
    public void testUnknownConditionKeyDoesNotMatchCurrentContext() {
        String json = MANIFEST.replace(
                "\"brightness\": \"dark\"",
                "\"futureCondition\": \"dark\"");
        Theme theme = ThemePackManifest.fromJson(json).findTheme("forest");
        assertNotNull(theme);

        ThemeAppearance appearance = theme.resolve(
                new ThemeResolveContext(Brightness.DARK, "windows", "en"));
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#4D7C3A"), appearance.color().resolveFallback());
        assertEquals(Contrast.DEFAULT, appearance.contrast());
        assertEquals("assets/wallpapers/forest.webp", background.path());
    }

    /// Tests that numeric contrast values are parsed as MonetFX contrast levels.
    @Test
    public void testParseNumericContrast() {
        String json = MANIFEST.replace("\"contrast\": \"default\"", "\"contrast\": 0.5");

        Theme theme = ThemePackManifest.fromJson(json).findTheme("forest");
        assertNotNull(theme);

        assertEquals(Contrast.MEDIUM, theme.appearance().contrast());
    }

    /// Parses the forest theme from the shared manifest.
    private static Theme parseForestTheme() {
        ThemePackManifest manifest = ThemePackManifest.fromJson(MANIFEST);

        assertEquals("example.nature", manifest.id());
        assertEquals("Nature", manifest.name());
        assertEquals("Example", manifest.authors().get(0));
        Theme theme = manifest.findTheme("forest");
        assertNotNull(theme);
        return theme;
    }
}
