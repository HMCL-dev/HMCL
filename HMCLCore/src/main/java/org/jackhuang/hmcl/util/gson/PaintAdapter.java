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
package org.jackhuang.hmcl.util.gson;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import javafx.scene.paint.*;

import java.io.IOException;

public final class PaintAdapter extends TypeAdapter<Paint> {

    @Override
    public void write(JsonWriter out, Paint value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }

        if (value instanceof Color) {
            Color color = (Color) value;
            int red = (int) Math.round(color.getRed() * 255.);
            int green = (int) Math.round(color.getGreen() * 255.);
            int blue = (int) Math.round(color.getBlue() * 255.);
            int opacity = (int) Math.round(color.getOpacity() * 255.);
            if (opacity < 255)
                out.value(String.format("#%02x%02x%02x%02x", red, green, blue, opacity));
            else
                out.value(String.format("#%02x%02x%02x", red, green, blue));
        } else if (value instanceof LinearGradient
                || value instanceof RadialGradient) {
            out.value(value.toString());
        } else {
            throw new JsonParseException("Unsupported Paint type: " + value.getClass().getName());
        }
    }

    @Override
    public Paint read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String value = in.nextString();
        return Paint.valueOf(value);
    }
}
