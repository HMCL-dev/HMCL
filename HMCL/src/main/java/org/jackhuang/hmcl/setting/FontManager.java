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
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
        Font font;

        // Recommended

        font = tryLoadLocalizedFont(Metadata.HMCL_CURRENT_DIRECTORY.resolve("font"));
        if (font != null)
            return font;

        font = tryLoadLocalizedFont(Metadata.HMCL_GLOBAL_DIRECTORY.resolve("font"));
        if (font != null)
            return font;

        // Legacy

        font = tryLoadDefaultFont(Metadata.HMCL_CURRENT_DIRECTORY);
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

        // Default

        String fcMatchPattern;
        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()
                && !(fcMatchPattern = I18n.getLocale().getFcMatchPattern()).isEmpty())
            return findByFcMatch(fcMatchPattern);
        else
            return null;
    });

    private static final ObjectProperty<FontReference> font = new SimpleObjectProperty<>();

    static {
        updateFont();
        LOG.info("Font: " + (font.get() != null ? font.get().family() : "System"));
    }

    private static void updateFont() {
        String fontFamily = config().getLauncherFontFamily();
        if (fontFamily == null)
            fontFamily = System.getProperty("hmcl.font.override");
        if (fontFamily == null)
            fontFamily = System.getenv("HMCL_FONT");

        if (fontFamily == null) {
            Font defaultFont = DEFAULT_FONT.get();
            font.set(defaultFont != null ? new FontReference(defaultFont) : null);
        } else {
            font.set(new FontReference(fontFamily));
        }
    }

    private static Font tryLoadDefaultFont(Path dir) {
        for (String extension : FONT_EXTENSIONS) {
            Path path = dir.resolve("font." + extension);
            if (Files.isRegularFile(path)) {
                LOG.info("Load font file: " + path);
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

    private static Font tryLoadLocalizedFont(Path dir) {
        Map<String, Map<String, Path>> fontFiles = LocaleUtils.findAllLocalizedFiles(dir, "font", Set.of(FONT_EXTENSIONS));
        if (fontFiles.isEmpty())
            return null;

        List<Locale> candidateLocales = I18n.getLocale().getCandidateLocales();

        for (Locale locale : candidateLocales) {
            Map<String, Path> extToFiles = fontFiles.get(LocaleUtils.toLanguageKey(locale));
            if (extToFiles != null) {
                for (String ext : FONT_EXTENSIONS) {
                    Path fontFile = extToFiles.get(ext);
                    if (fontFile != null) {
                        LOG.info("Load font file: " + fontFile);
                        try {
                            Font font = Font.loadFont(
                                    fontFile.toAbsolutePath().normalize().toUri().toURL().toExternalForm(),
                                    DEFAULT_FONT_SIZE);
                            if (font != null)
                                return font;
                        } catch (MalformedURLException ignored) {
                        }

                        LOG.warning("Failed to load font " + fontFile);
                    }
                }
            }
        }

        return null;
    }

    public static Font findByFcMatch(String pattern) {
        Path fcMatch = SystemUtils.which("fc-match");
        if (fcMatch == null)
            return null;

        try {
            String result = SystemUtils.run(fcMatch.toString(),
                    pattern,
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

            LOG.info("Load font file: " + path);
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

    public static ReadOnlyObjectProperty<FontReference> fontProperty() {
        return font;
    }

    public static FontReference getFont() {
        return font.get();
    }

    public static void setFontFamily(String fontFamily) {
        config().setLauncherFontFamily(fontFamily);
        updateFont();
    }

    // https://github.com/HMCL-dev/HMCL/issues/4072
    public record FontReference(@NotNull String family, @Nullable String style) {
        public FontReference {
            Objects.requireNonNull(family);
        }

        public FontReference(@NotNull String family) {
            this(family, null);
        }

        public FontReference(@NotNull Font font) {
            this(font.getFamily(), font.getStyle());
        }
    }

    private FontManager() {
    }
}

