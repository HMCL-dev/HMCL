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
package org.jackhuang.hmcl.theme;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;
import javafx.beans.property.Property;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

/// @author Glavo
@JsonAdapter(ThemeColor.TypeAdapter.class)
@JsonSerializable
public record ThemeColor(@NotNull String name, @NotNull Color color) {

    public static final ThemeColor DEFAULT = new ThemeColor("blue", Color.web("#5C6BC0"));

    public static final List<ThemeColor> STANDARD_COLORS = List.of(
            DEFAULT,
            new ThemeColor("darker_blue", Color.web("#283593")),
            new ThemeColor("green", Color.web("#43A047")),
            new ThemeColor("orange", Color.web("#E67E22")),
            new ThemeColor("purple", Color.web("#9C27B0")),
            new ThemeColor("red", Color.web("#B71C1C"))
    );

    public static String getColorDisplayName(Color c) {
        return c != null ? String.format("#%02X%02X%02X",
                Math.round(c.getRed() * 255.0D),
                Math.round(c.getGreen() * 255.0D),
                Math.round(c.getBlue() * 255.0D))
                : null;
    }

    public static String getColorDisplayNameWithOpacity(Color c, double opacity) {
        return c != null ? String.format("#%02X%02X%02X%02X",
                Math.round(c.getRed() * 255.0D),
                Math.round(c.getGreen() * 255.0D),
                Math.round(c.getBlue() * 255.0D),
                Math.round(opacity * 255.0))
                : null;
    }

    public static @Nullable ThemeColor of(String name) {
        if (name == null)
            return null;

        if (!name.startsWith("#")) {
            for (ThemeColor color : STANDARD_COLORS) {
                if (name.equalsIgnoreCase(color.name()))
                    return color;
            }
        }

        try {
            return new ThemeColor(name, Color.web(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Contract("null -> null; !null -> !null")
    public static ThemeColor of(Color color) {
        return color != null ? new ThemeColor(getColorDisplayName(color), color) : null;
    }

    private static final class BidirectionalBinding implements InvalidationListener, WeakListener {
        private final WeakReference<ColorPicker> colorPickerRef;
        private final WeakReference<Property<ThemeColor>> propertyRef;
        private final int hashCode;

        private boolean updating = false;

        private BidirectionalBinding(ColorPicker colorPicker, Property<ThemeColor> property) {
            this.colorPickerRef = new WeakReference<>(colorPicker);
            this.propertyRef = new WeakReference<>(property);
            this.hashCode = System.identityHashCode(colorPicker) ^ System.identityHashCode(property);
        }

        @Override
        public void invalidated(Observable sourceProperty) {
            if (!updating) {
                final ColorPicker colorPicker = colorPickerRef.get();
                final Property<ThemeColor> property = propertyRef.get();

                if (colorPicker == null || property == null) {
                    if (colorPicker != null) {
                        colorPicker.valueProperty().removeListener(this);
                    }

                    if (property != null) {
                        property.removeListener(this);
                    }
                } else {
                    updating = true;
                    try {
                        if (property == sourceProperty) {
                            ThemeColor newValue = property.getValue();
                            colorPicker.setValue(newValue != null ? newValue.color() : null);
                        } else {
                            Color newValue = colorPicker.getValue();
                            property.setValue(newValue != null ? ThemeColor.of(newValue) : null);
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return colorPickerRef.get() == null || propertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof BidirectionalBinding that))
                return false;

            final ColorPicker colorPicker = this.colorPickerRef.get();
            final Property<ThemeColor> property = this.propertyRef.get();

            final ColorPicker thatColorPicker = that.colorPickerRef.get();
            final Property<?> thatProperty = that.propertyRef.get();

            if (colorPicker == null || property == null || thatColorPicker == null || thatProperty == null)
                return false;

            return colorPicker == thatColorPicker && property == thatProperty;
        }
    }

    public static void bindBidirectional(ColorPicker colorPicker, Property<ThemeColor> property) {
        var binding = new BidirectionalBinding(colorPicker, property);

        colorPicker.valueProperty().removeListener(binding);
        property.removeListener(binding);

        ThemeColor themeColor = property.getValue();
        colorPicker.setValue(themeColor != null ? themeColor.color() : null);

        colorPicker.valueProperty().addListener(binding);
        property.addListener(binding);
    }

    static final class TypeAdapter extends com.google.gson.TypeAdapter<ThemeColor> {
        @Override
        public void write(JsonWriter out, ThemeColor value) throws IOException {
            out.value(value.name());
        }

        @Override
        public ThemeColor read(JsonReader in) throws IOException {
            return Objects.requireNonNullElse(of(in.nextString()), ThemeColor.DEFAULT);
        }
    }
}
