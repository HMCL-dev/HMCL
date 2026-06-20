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
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests for theme-pack manifest parsing and conditional override resolution.
@NotNullByDefault
public final class ThemePackManifestTest {

    /// A representative theme-pack manifest containing default appearance fields and conditional overrides.
    private static final String MANIFEST = """
            {
              "formatVersion": 1,
              "id": "example.nature",
              "version": "1.0.0",
              "name": "Nature",
              "authors": ["Example"],
              "themes": [
                {
                  "id": "forest",
                  "name": "Forest",
                  "family": "forest",
                  "thumbnail": "assets/thumbnails/forest.png",
                  "primaryColor": "#4D7C3A",
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
                      "primaryColor": "#6FA65A",
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
                        "arch": ["arm64", "riscv64"]
                      },
                      "colorStyle": "tonal_spot"
                    }
                  ]
                }
              ]
            }
            """;

    /// Tests that matching overrides are applied in declaration order and deeply merge backgrounds.
    @Test
    public void testResolveMatchingOverrides() {
        ThemePreset preset = parseForestPreset();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.DARK, "auto", "windows", "x86_64", "en");

        ThemeAppearance appearance = preset.resolve(context);
        Theme theme = appearance.toTheme(context);
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertEquals(ThemeColor.of("#6FA65A"), appearance.primaryColor());
        assertEquals(Brightness.DARK, theme.brightness());
        assertEquals(ColorStyle.FIDELITY, theme.colorStyle());
        assertEquals(Contrast.HIGH, theme.contrast());
        assertEquals(ThemeBackground.Type.IMAGE, background.effectiveType());
        assertEquals("assets/wallpapers/forest-dark.webp", background.path());
        assertEquals(0.9, background.opacity());
    }

    /// Tests that array condition values match any listed value.
    @Test
    public void testConditionArrayMatchesAnyValue() {
        ThemePreset preset = parseForestPreset();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "light", "linux", "arm64", "en");

        Theme theme = preset.toTheme(context);

        assertEquals(Brightness.LIGHT, theme.brightness());
        assertEquals(ColorStyle.TONAL_SPOT, theme.colorStyle());
        assertEquals(Contrast.DEFAULT, theme.contrast());
    }

    /// Tests that non-matching overrides leave default fields unchanged.
    @Test
    public void testNonMatchingOverridesAreIgnored() {
        ThemePreset preset = parseForestPreset();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "light", "linux", "x86_64", "en");

        ThemeAppearance appearance = preset.resolve(context);
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertEquals(ThemeColor.of("#4D7C3A"), appearance.primaryColor());
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
                  "formatVersion": 1,
                  "id": "example.empty-condition",
                  "version": "1.0.0",
                  "name": "Empty Condition",
                  "themes": [
                    {
                      "id": "current",
                      "name": "Current",
                      "primaryColor": "#111111",
                      "overrides": [
                        {
                          "condition": {},
                          "primaryColor": "#222222"
                        }
                      ]
                    }
                  ]
                }
                """);
        ThemePreset preset = manifest.findTheme("current");
        assertNotNull(preset);

        ThemeAppearance appearance = preset.resolve(
                new ThemeResolveContext(Brightness.LIGHT, "light", "linux", "x86_64", "en"));

        assertEquals(ThemeColor.of("#222222"), appearance.primaryColor());
    }

    /// Tests that unsupported override fields are rejected.
    @Test
    public void testRejectUnsupportedOverrideField() {
        String json = MANIFEST.replace(
                "\"primaryColor\": \"#6FA65A\"",
                "\"primaryColor\": \"#6FA65A\", \"unknownAppearance\": true");

        assertThrows(JsonParseException.class, () -> ThemePackManifest.fromJson(json));
    }

    /// Tests that unknown condition keys are accepted but do not match the current context.
    @Test
    public void testUnknownConditionKeyDoesNotMatchCurrentContext() {
        String json = MANIFEST.replace(
                "\"brightness\": \"dark\"",
                "\"futureCondition\": \"dark\"");
        ThemePreset preset = ThemePackManifest.fromJson(json).findTheme("forest");
        assertNotNull(preset);

        ThemeAppearance appearance = preset.resolve(
                new ThemeResolveContext(Brightness.DARK, "auto", "windows", "x86_64", "en"));
        ThemeBackground background = appearance.background();
        assertNotNull(background);

        assertEquals(ThemeColor.of("#4D7C3A"), appearance.primaryColor());
        assertEquals(Contrast.DEFAULT, appearance.contrast());
        assertEquals("assets/wallpapers/forest.webp", background.path());
    }

    /// Tests that numeric contrast values are parsed as MonetFX contrast levels.
    @Test
    public void testParseNumericContrast() {
        String json = MANIFEST.replace("\"contrast\": \"default\"", "\"contrast\": 0.5");

        ThemePreset preset = ThemePackManifest.fromJson(json).findTheme("forest");
        assertNotNull(preset);

        assertEquals(Contrast.MEDIUM, preset.appearance().contrast());
    }

    /// Parses the forest theme from the shared manifest.
    private static ThemePreset parseForestPreset() {
        ThemePackManifest manifest = ThemePackManifest.fromJson(MANIFEST);

        assertEquals("example.nature", manifest.id());
        assertEquals("Nature", manifest.name());
        assertEquals("Example", manifest.authors().get(0));
        ThemePreset preset = manifest.findTheme("forest");
        assertNotNull(preset);
        return preset;
    }
}
