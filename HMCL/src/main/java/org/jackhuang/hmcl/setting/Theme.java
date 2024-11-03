/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonAdapter(Theme.TypeAdapter.class)
public class Theme {
    public static final Theme BLUE = new Theme("blue", "#5C6BC0");
    public static final Color BLACK = Color.web("#292929");
    public static final Color[] SUGGESTED_COLORS = new Color[]{
            Color.web("#3D6DA3"), // blue
            Color.web("#283593"), // dark blue
            Color.web("#43A047"), // green
            Color.web("#E67E22"), // orange
            Color.web("#9C27B0"), // purple
            Color.web("#B71C1C")  // red
    };

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static Optional<Font> font;

    private static Optional<Font> tryLoadFont() {
        //noinspection OptionalAssignedToNull
        if (font != null) {
            return font;
        }

        Path path = Paths.get("font.ttf");
        if (!Files.isRegularFile(path)) {
            path = Paths.get("font.otf");
        }

        if (Files.isRegularFile(path)) {
            try {
                return font = Optional.ofNullable(Font.loadFont(path.toAbsolutePath().toUri().toURL().toExternalForm(), 0));
            } catch (MalformedURLException ignored) {
            }
        }

        return font = Optional.empty();
    }

    public static Theme getTheme() {
        Theme theme = config().getTheme();
        return theme == null ? BLUE : theme;
    }

    private final Color paint;
    private final String color;
    private final String name;

    Theme(String name, String color) {
        this.name = name;
        this.color = Objects.requireNonNull(color);
        this.paint = Color.web(color);
    }

    public String getName() {
        return name;
    }

    public String getColor() {
        return color;
    }

    public boolean isCustom() {
        return name.startsWith("#");
    }

    public boolean isLight() {
        return paint.grayscale().getRed() >= 0.5;
    }

    public Color getForegroundColor() {
        return isLight() ? Color.BLACK : Color.WHITE;
    }

    private static String rgba(Color color, double opacity) {
        return String.format("rgba(%d, %d, %d, %.1f)",
                (int) Math.ceil(color.getRed() * 256),
                (int) Math.ceil(color.getGreen() * 256),
                (int) Math.ceil(color.getBlue() * 256),
                opacity);
    }

    public String[] getStylesheets(String overrideFontFamily) {
        String css = "/assets/css/blue.css";

        String fontFamily = overrideFontFamily == null
                ? System.getProperty("hmcl.font.override", System.getenv("HMCL_FONT"))
                : overrideFontFamily;

        String fontStyle = null;
        if (fontFamily == null) {
            Optional<Font> font = tryLoadFont();
            if (font.isPresent()) {
                fontFamily = font.get().getFamily();
                fontStyle = font.get().getStyle();
            }
        }

        if (fontFamily != null || !this.color.equalsIgnoreCase(BLUE.color)) {
            Color textFill = getForegroundColor();

            StringBuilder themeBuilder = new StringBuilder(512);
            themeBuilder.append(".root {")
                    .append("-fx-base-color:").append(color).append(';')
                    .append("-fx-base-darker-color: derive(-fx-base-color, -10%);")
                    .append("-fx-base-check-color: derive(-fx-base-color, 30%);")
                    .append("-fx-rippler-color:").append(rgba(paint, 0.3)).append(';')
                    .append("-fx-base-rippler-color: derive(").append(rgba(paint, 0.3)).append(", 100%);")
                    .append("-fx-base-disabled-text-fill:").append(rgba(textFill, 0.7)).append(";")
                    .append("-fx-base-text-fill:").append(getColorDisplayName(getForegroundColor())).append(";")
                    .append("-theme-thumb:").append(rgba(paint, 0.7)).append(";");

            if (fontFamily == null)
                // https://github.com/HMCL-dev/HMCL/pull/3423
                themeBuilder.append("-fx-font-family: -fx-base-font-family;");
            else
                themeBuilder.append("-fx-font-family:\"").append(fontFamily).append("\";");

            if (fontStyle != null && !fontStyle.isEmpty())
                themeBuilder.append("-fx-font-style:\"").append(fontStyle).append("\";");

            themeBuilder.append('}');

            if (FXUtils.JAVAFX_MAJOR_VERSION >= 17)
                // JavaFX 17+ support loading stylesheets from data URIs
                // https://bugs.openjdk.org/browse/JDK-8267554
                css = "data:text/css;charset=UTF-8;base64," + Base64.getEncoder().encodeToString(themeBuilder.toString().getBytes(StandardCharsets.UTF_8));
            else
                try {
                    File temp = File.createTempFile("hmcl", ".css");
                    // For JavaFX 17 or earlier, CssParser uses the default charset
                    // https://bugs.openjdk.org/browse/JDK-8279328
                    FileUtils.writeText(temp, themeBuilder.toString(), Charset.defaultCharset());
                    temp.deleteOnExit();
                    css = temp.toURI().toString();
                } catch (IOException | NullPointerException e) {
                    LOG.error("Unable to create theme stylesheet. Fallback to blue theme.", e);
                }
        }

        return new String[]{css, "/assets/css/root.css"};
    }

    public static Theme custom(String color) {
        if (!color.startsWith("#"))
            throw new IllegalArgumentException();
        return new Theme(color, color);
    }

    public static Optional<Theme> getTheme(String name) {
        if (name == null)
            return Optional.empty();
        else if (name.startsWith("#"))
            try {
                Color.web(name);
                return Optional.of(custom(name));
            } catch (IllegalArgumentException ignore) {
            }
        else {
            String color = null;
            switch (name.toLowerCase(Locale.ROOT)) {
                case "blue":
                    return Optional.of(BLUE);
                case "darker_blue":
                    color = "#283593";
                    break;
                case "green":
                    color = "#43A047";
                    break;
                case "orange":
                    color = "#E67E22";
                    break;
                case "purple":
                    color = "#9C27B0";
                    break;
                case "red":
                    color = "#F44336";
            }
            if (color != null)
                return Optional.of(new Theme(name, color));
        }

        return Optional.empty();
    }

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02X%02X%02X", Math.round(c.getRed() * 255.0D), Math.round(c.getGreen() * 255.0D), Math.round(c.getBlue() * 255.0D)) : null;
    }

    private static ObjectBinding<Color> FOREGROUND_FILL;

    public static ObjectBinding<Color> foregroundFillBinding() {
        if (FOREGROUND_FILL == null)
            FOREGROUND_FILL = Bindings.createObjectBinding(
                    () -> Theme.getTheme().getForegroundColor(),
                    config().themeProperty()
            );

        return FOREGROUND_FILL;
    }

    public static Color blackFill() {
        return BLACK;
    }

    public static Color whiteFill() {
        return Color.WHITE;
    }

    public static class TypeAdapter extends com.google.gson.TypeAdapter<Theme> {
        @Override
        public void write(JsonWriter out, Theme value) throws IOException {
            out.value(value.getName().toLowerCase(Locale.ROOT));
        }

        @Override
        public Theme read(JsonReader in) throws IOException {
            return getTheme(in.nextString()).orElse(Theme.BLUE);
        }
    }
}
