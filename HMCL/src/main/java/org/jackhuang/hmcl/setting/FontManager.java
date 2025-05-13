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

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class FontManager {
    private static Font tryLoadDefaultFont(Path dir) {
        String[] fileNames = {"font.ttf", "font.otf", "font.woff"};

        for (String fileName : fileNames) {
            Path path = dir.resolve(fileName);
            if (Files.isRegularFile(path)) {
                try {
                    Font font = Font.loadFont(path.toUri().toURL().toExternalForm(), 0);
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
        if (thisJar != null && thisJar.getParent() != null)
            return tryLoadDefaultFont(thisJar.getParent());

        return null;
    });

    static final ObservableObjectValue<Font> fontProperty = Bindings.createObjectBinding(() -> {
        String fontFamily = config().getLauncherFontFamily();
        if (fontFamily == null)
            fontFamily = System.getProperty("hmcl.font.override", System.getenv("HMCL_FONT"));
        if (fontFamily != null)
            return Font.font(fontFamily);

        Font font = DEFAULT_FONT.get();
        if (font != null)
            return font;

        return Font.getDefault();
    }, config().launcherFontFamilyProperty());

    public static Font getFont() {
        return fontProperty.get();
    }

    private FontManager() {
    }
}
