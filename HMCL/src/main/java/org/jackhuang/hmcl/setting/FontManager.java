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

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class FontManager {

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

    private static final ObjectProperty<Font> fontProperty;

    static {
        String fontFamily = config().getLauncherFontFamily();
        if (fontFamily == null)
            fontFamily = System.getProperty("hmcl.font.override");
        if (fontFamily == null)
            fontFamily = System.getenv("HMCL_FONT");

        Font font = fontFamily == null ? DEFAULT_FONT.get() : Font.font(fontFamily, DEFAULT_FONT_SIZE);
        fontProperty = new SimpleObjectProperty<>(font);

        LOG.info("Font: " + (font != null ? font.getName() : Font.getDefault().getName()));
        fontProperty.addListener((obs, oldValue, newValue) -> {
            if (newValue != null)
                config().setLauncherFontFamily(newValue.getFamily());
            else
                config().setLauncherFontFamily(null);
        });
    }

    private static Font tryLoadDefaultFont(Path dir) {
        String[] fileNames = {"font.ttf", "font.otf", "font.woff"};

        for (String fileName : fileNames) {
            Path path = dir.resolve(fileName);
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
            String path = SystemUtils.run(fcMatch.toString(),
                    ":lang=" + Locale.getDefault().toLanguageTag(),
                    "--format", "%{file}").trim();
            Path file = Paths.get(path).toAbsolutePath().normalize();
            if (!Files.isRegularFile(file)) {
                LOG.warning("Font file does not exist: " + path);
                return null;
            }

            Font font = Font.loadFont(file.toUri().toURL().toExternalForm(), DEFAULT_FONT_SIZE);
            if (font == null)
                LOG.warning("Failed to load font from " + path);
            return font;
        } catch (Throwable e) {
            LOG.warning("Failed to get default font with fc-match", e);
            return null;
        }
    }

    public static ObjectProperty<Font> fontProperty() {
        return fontProperty;
    }

    public static Font getFont() {
        return fontProperty.get();
    }

    public static void setFont(Font font) {
        fontProperty.set(font);
    }

    public static void setFontFamily(String fontFamily) {
        setFont(fontFamily != null ? Font.font(fontFamily, DEFAULT_FONT_SIZE) : null);
    }

    private FontManager() {
    }
}
