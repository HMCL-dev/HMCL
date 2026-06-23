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
import org.jackhuang.hmcl.setting.BackgroundType;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
              "authors": [
                {
                  "name": "Example"
                }
              ],
              "theme": {
                "id": "forest",
                "name": "Forest",
                "authors": [
                  {
                    "name": "Forest Artist"
                  }
                ],
                "thumbnail": "assets/thumbnails/forest.png",
                "color": "#4D7C3A",
                "colorStyle": "fidelity",
                "contrast": "standard",
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
        ThemeBackgroundSettings background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#6FA65A"), appearance.color().resolveFallback());
        assertEquals(Brightness.DARK, resolvedTheme.brightness());
        assertEquals(ColorStyle.FIDELITY, resolvedTheme.colorStyle());
        assertEquals(Contrast.HIGH, resolvedTheme.contrast());
        ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());
        assertEquals("assets/wallpapers/forest-dark.webp", image.path());
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
        ThemeBackgroundSettings background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertInstanceOf(ThemeColorSource.Custom.class, appearance.color());
        assertEquals(ThemeColor.of("#4D7C3A"), appearance.color().resolveFallback());
        assertEquals(ColorStyle.FIDELITY, appearance.colorStyle());
        assertEquals(Contrast.DEFAULT, appearance.contrast());
        ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());
        assertEquals("assets/wallpapers/forest.webp", image.path());
        assertEquals(0.8, background.opacity());
    }

    /// Tests that an explicit theme brightness controls the resolved launcher brightness.
    @Test
    public void testExplicitBrightnessControlsResolvedTheme() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.brightness",
                  "version": "1.0.0",
                  "name": "Brightness",
                  "theme": {
                    "brightness": "dark"
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);

        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "en");
        ThemeAppearance appearance = theme.resolve(context);
        ResolvedTheme resolvedTheme = appearance.toResolvedTheme(context);

        assertEquals(Brightness.DARK, appearance.brightness());
        assertEquals(Brightness.DARK, resolvedTheme.brightness());
    }

    /// Tests that a theme without brightness keeps the brightness supplied by the resolution context.
    @Test
    public void testMissingBrightnessUsesResolutionContext() {
        Theme theme = parseForestTheme();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "en");

        ThemeAppearance appearance = theme.resolve(context);
        ResolvedTheme resolvedTheme = appearance.toResolvedTheme(context);

        assertNull(appearance.brightness());
        assertEquals(Brightness.LIGHT, resolvedTheme.brightness());
    }

    /// Tests that the built-in background type can reference a launcher built-in wallpaper.
    @Test
    public void testParseBuiltinBackground() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.builtin-background",
                  "version": "1.0.0",
                  "name": "Builtin Background",
                  "theme": {
                    "background": {
                      "type": "builtin",
                      "id": "2021-08-26",
                      "opacity": 0.75
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        ThemeBackgroundSettings background = theme.appearance().background();
        assertNotNull(background);

        ThemeBackground.Builtin builtin = assertInstanceOf(ThemeBackground.Builtin.class, background.source());
        assertEquals(BackgroundType.BUILTIN_WALLPAPER_2021_08_26_ID, builtin.id());
        assertEquals(0.75, background.opacity());
    }

    /// Tests that network background cache policies are parsed from theme packs.
    @Test
    public void testParseNetworkBackgroundCachePolicy() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.network-background-cache",
                  "version": "1.0.0",
                  "name": "Network Background Cache",
                  "theme": {
                    "background": {
                      "type": "network",
                      "url": "https://example.com/wallpaper.png",
                      "cache": "disabled"
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        ThemeBackgroundSettings background = theme.appearance().background();
        assertNotNull(background);

        ThemeBackground.Network network = assertInstanceOf(ThemeBackground.Network.class, background.source());
        assertEquals("https://example.com/wallpaper.png", network.url());
        assertEquals(NetworkBackgroundImageCachePolicy.DISABLED, network.cache());
    }

    /// Tests that background fallback and load behavior are parsed from theme packs.
    @Test
    public void testParseBackgroundLoadingControls() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.background-loading",
                  "version": "1.0.0",
                  "name": "Background Loading",
                  "theme": {
                    "background": {
                      "type": "network",
                      "url": "https://example.com/wallpaper.png",
                      "fallback": {
                        "type": "paint",
                        "paint": "#123456"
                      },
                      "loadPolicy": "wait_for_background"
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        ThemeBackgroundSettings background = theme.appearance().background();
        assertNotNull(background);

        ThemeBackground.Paint fallback = assertInstanceOf(ThemeBackground.Paint.class, background.fallback());
        assertEquals("#123456", fallback.paint());
        assertEquals(BackgroundLoadPolicy.WAIT_FOR_BACKGROUND, background.loadPolicy());
    }

    /// Tests that unsupported background types fall back to the inherited/default background.
    @Test
    public void testUnsupportedBackgroundTypeFallsBackToDefault() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.classic-background",
                  "version": "1.0.0",
                  "name": "Classic Background",
                  "theme": {
                    "background": {
                      "type": "classic"
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);

        assertNull(theme.appearance().background());
    }

    /// Tests that unsupported brightness values fall back to the resolution context.
    @Test
    public void testUnsupportedBrightnessFallsBackToResolutionContext() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.adaptive-brightness",
                  "version": "1.0.0",
                  "name": "Adaptive Brightness",
                  "theme": {
                    "brightness": "adaptive"
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);

        ThemeResolveContext context = new ThemeResolveContext(Brightness.DARK, "linux", "en");
        assertNull(theme.appearance().brightness());
        assertEquals(Brightness.DARK, theme.toResolvedTheme(context).brightness());
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

    /// Tests that a package ID must be safe to use as an installed theme-pack file name.
    @Test
    public void testPackageIdMustBeFileNameSafe() {
        assertThrows(IllegalArgumentException.class, () -> ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example/unsafe",
                  "version": "1.0.0",
                  "name": "Unsafe",
                  "theme": {}
                }
                """));
    }

    /// Tests that display names and descriptions accept localized text objects.
    @Test
    public void testLocalizedTextFields() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.localized",
                  "version": "1.0.0",
                  "name": {
                    "default": "Localized Pack",
                    "zh-Hans": "本地化主题包"
                  },
                  "description": {
                    "default": "Localized pack description"
                  },
                  "themes": [
                    {
                      "id": "localized",
                      "name": {
                        "default": "Localized Theme",
                        "zh-Hans": "本地化主题"
                      },
                      "description": {
                        "default": "Localized theme description"
                      },
                      "color": "#111111"
                    }
                  ]
                }
                """);

        Theme theme = manifest.findTheme("localized");
        assertNotNull(theme);
        List<Locale> rootLocale = List.of(Locale.ROOT);
        assertEquals("Localized Pack", manifest.name().getText(rootLocale));
        assertNotNull(manifest.description());
        assertEquals("Localized pack description", manifest.description().getText(rootLocale));
        assertNotNull(theme.name());
        assertEquals("Localized Theme", theme.name().getText(rootLocale));
        assertNotNull(theme.description());
        assertEquals("Localized theme description", theme.description().getText(rootLocale));
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
        assertEquals("Single", theme.displayName());
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
        ThemeBackgroundSettings background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#6FA65A"), appearance.color().resolveFallback());
        ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());
        assertEquals("assets/wallpapers/forest.webp", image.path());
    }

    /// Tests that additional wallpaper color source fields are ignored.
    @Test
    public void testWallpaperColorIgnoresFallbackField() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.wallpaper-fallback",
                  "version": "1.0.0",
                  "name": "Wallpaper Fallback",
                  "theme": {
                    "color": {
                      "source": "wallpaper",
                      "fallback": "#4D7C3A"
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);

        assertInstanceOf(ThemeColorSource.Wallpaper.class, theme.appearance().color());
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
        ThemeBackgroundSettings background = appearance.background();
        assertNotNull(background);

        assertNotNull(appearance.color());
        assertEquals(ThemeColor.of("#4D7C3A"), appearance.color().resolveFallback());
        assertEquals(Contrast.DEFAULT, appearance.contrast());
        ThemeBackground.Image image = assertInstanceOf(ThemeBackground.Image.class, background.source());
        assertEquals("assets/wallpapers/forest.webp", image.path());
    }

    /// Tests that numeric contrast values are parsed as MonetFX contrast levels.
    @Test
    public void testParseNumericContrast() {
        String json = MANIFEST.replace("\"contrast\": \"standard\"", "\"contrast\": 0.5");

        Theme theme = ThemePackManifest.fromJson(json).findTheme("forest");
        assertNotNull(theme);

        assertEquals(Contrast.MEDIUM, theme.appearance().contrast());
    }

    /// Tests that malformed optional appearance fields are ignored instead of rejecting the manifest.
    @Test
    public void testInvalidOptionalAppearanceFieldsFallbackToDefaults() {
        ThemePackManifest manifest = ThemePackManifest.fromJson("""
                {
                  "$schema": "https://schemas.glavo.site/hmcl/theme-pack/1.0.0",
                  "id": "example.invalid-appearance",
                  "version": "1.0.0",
                  "name": "Invalid Appearance",
                  "theme": {
                    "color": "not-a-color",
                    "brightness": "adaptive",
                    "colorStyle": "unknown",
                    "contrast": 2,
                    "background": {
                      "type": "paint",
                      "paint": "#111111",
                      "opacity": 2
                    },
                    "titleBar": {
                      "transparent": "yes"
                    }
                  }
                }
                """);
        Theme theme = manifest.findTheme(null);
        assertNotNull(theme);
        ThemeAppearance appearance = theme.appearance();
        ThemeResolveContext context = new ThemeResolveContext(Brightness.LIGHT, "linux", "en");

        assertNull(appearance.color());
        assertNull(appearance.brightness());
        assertNull(appearance.colorStyle());
        assertNull(appearance.contrast());
        assertNull(appearance.titleBar());
        assertEquals(Brightness.LIGHT, appearance.toResolvedTheme(context).brightness());
        assertEquals(ResolvedTheme.DEFAULT.colorStyle(), appearance.toResolvedTheme(context).colorStyle());
        assertEquals(Contrast.DEFAULT, appearance.toResolvedTheme(context).contrast());

        ThemeBackgroundSettings background = appearance.background();
        assertNotNull(background);
        assertInstanceOf(ThemeBackground.Paint.class, background.source());
        assertNull(background.opacity());
    }

    /// Parses the forest theme from the shared manifest.
    private static Theme parseForestTheme() {
        ThemePackManifest manifest = ThemePackManifest.fromJson(MANIFEST);

        assertEquals("example.nature", manifest.id());
        assertEquals("Nature", manifest.displayName());
        assertEquals("Example", manifest.authors().get(0).displayName());
        Theme theme = manifest.findTheme("forest");
        assertNotNull(theme);
        assertEquals("Forest Artist", theme.authors().get(0).displayName());
        return theme;
    }
}
