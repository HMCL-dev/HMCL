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
package org.jackhuang.hmcl.setting;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class FontManager {

    public static final String[] FONT_EXTENSIONS = {
            "ttf", "otf", "woff"
    };

    public static final double DEFAULT_FONT_SIZE = 12.0f;

    private static final Lazy<Font> DEFAULT_FONT = new Lazy<>(() -> {
        Font font = tryLoadDefaultFont(Metadata.HMCL_CURRENT_DIRECTORY);
        if (font != null)
            return font;

        font = tryLoadDefaultFont(Metadata.CURRENT_DIRECTORY);
        if (font != null)
            return font;

        font = tryLoadDefaultFont(Metadata.HMCL_GLOBAL_DIRECTORY);
        if (font != null)
            return font;

        Path thisJar = JarUtils.thisJarPath();
        if (thisJar != null && thisJar.getParent() != null) {
            font = tryLoadDefaultFont(thisJar.getParent());
            if (font != null)
                return font;
        }

        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()
                && Locale.getDefault() != Locale.ROOT
                && !"en".equals(Locale.getDefault().getLanguage()))
            return findByFcMatch();
        else
            return null;
    });

    private static final ObjectProperty<FontReference> fontProperty;

    static {
        String fontFamily = config().getLauncherFontFamily();
        if (fontFamily == null)
            fontFamily = System.getProperty("hmcl.font.override");
        if (fontFamily == null)
            fontFamily = System.getenv("HMCL_FONT");

        FontReference fontReference;
        if (fontFamily == null) {
            Font defaultFont = DEFAULT_FONT.get();
            fontReference = defaultFont != null ? new FontReference(defaultFont) : null;
        } else
            fontReference = new FontReference(fontFamily);

        fontProperty = new SimpleObjectProperty<>(fontReference);

        LOG.info("Font: " + (fontReference != null ? fontReference.getFamily() : "System"));
        fontProperty.addListener((obs, oldValue, newValue) -> {
            if (newValue != null)
                config().setLauncherFontFamily(newValue.getFamily());
            else
                config().setLauncherFontFamily(null);
        });
    }

    private static Font tryLoadDefaultFont(Path dir) {
        for (String extension : FONT_EXTENSIONS) {
            Path path = dir.resolve("font." + extension);
            if (Files.isRegularFile(path)) {
                try {
                    Font font = Font.loadFont(path.toUri().toURL().toExternalForm(), DEFAULT_FONT_SIZE);
                    if (font != null) {
                        return font;
                    }
                } catch (MalformedURLException ignored) {
                }

                LOG.warning("Failed to load font " + path);
            }
        }

        return null;
    }

    public static Font findByFcMatch() {
        Path fcMatch = SystemUtils.which("fc-match");
        if (fcMatch == null)
            return null;

        try {
            Locale locale = Locale.getDefault();
            if (locale.getLanguage().equals("lzh"))
                locale = Locale.TRADITIONAL_CHINESE;

            String result = SystemUtils.run(fcMatch.toString(),
                    ":lang=" + locale.toLanguageTag(),
                    "--format", "%{family}\\n%{file}").trim();

            String[] results = result.split("\\n");
            if (results.length != 2 || results[0].isEmpty() || results[1].isEmpty()) {
                LOG.warning("Unexpected output from fc-match: " + result);
                return null;
            }

            String family = results[0].trim();
            String path = results[1];

            Path file = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                LOG.warning("Font file does not exist: " + path);
                return null;
            }

            Font[] fonts = Font.loadFonts(file.toUri().toURL().toExternalForm(), DEFAULT_FONT_SIZE);
            if (fonts == null) {
                LOG.warning("Failed to load font from " + path);
                return null;
            } else if (fonts.length == 0) {
                LOG.warning("No fonts loaded from " + path);
                return null;
            }

            for (Font font : fonts) {
                if (font.getFamily().equalsIgnoreCase(family)) {
                    return font;
                }
            }

            if (family.indexOf(',') >= 0) {
                for (String candidateFamily : family.split(",")) {
                    for (Font font : fonts) {
                        if (font.getFamily().equalsIgnoreCase(candidateFamily)) {
                            return font;
                        }
                    }
                }
            }

            LOG.warning(String.format("Family '%s' not found in font file '%s'", family, path));
            return fonts[0];
        } catch (Throwable e) {
            LOG.warning("Failed to get default font with fc-match", e);
            return null;
        }
    }

    public static ObjectProperty<FontReference> fontProperty() {
        return fontProperty;
    }

    public static FontReference getFont() {
        return fontProperty.get();
    }

    public static void setFont(FontReference font) {
        fontProperty.set(font);
    }

    public static void setFontFamily(String fontFamily) {
        setFont(fontFamily != null ? new FontReference(fontFamily) : null);
    }

    // https://github.com/HMCL-dev/HMCL/issues/4072
    public static final class FontReference {
        private final @NotNull String family;
        private final @Nullable String style;

        public FontReference(@NotNull String family) {
            this.family = Objects.requireNonNull(family);
            this.style = null;
        }

        public FontReference(@NotNull Font font) {
            this.family = font.getFamily();
            this.style = font.getStyle();
        }

        public @NotNull String getFamily() {
            return family;
        }

        public @Nullable String getStyle() {
            return style;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FontReference))
                return false;
            FontReference that = (FontReference) o;
            return Objects.equals(family, that.family) && Objects.equals(style, that.style);
        }

        @Override
        public int hashCode() {
            return Objects.hash(family, style);
        }

        @Override
        public String toString() {
            return String.format("FontReference[family='%s', style='%s']", family, style);
        }
    }

    private FontManager() {
    }
}
