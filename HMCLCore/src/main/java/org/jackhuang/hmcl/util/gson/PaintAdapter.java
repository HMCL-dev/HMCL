// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
