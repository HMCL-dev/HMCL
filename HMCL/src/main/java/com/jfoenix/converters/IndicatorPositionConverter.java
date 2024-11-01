/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.jfoenix.converters;

import com.jfoenix.controls.JFXSlider.IndicatorPosition;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/**
 * Converts the CSS for -fx-indicator-position items into IndicatorPosition.
 * it's used in JFXSlider.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class IndicatorPositionConverter extends StyleConverter<String, IndicatorPosition> {
    // lazy, thread-safe instatiation
    private static class Holder {
        static final IndicatorPositionConverter INSTANCE = new IndicatorPositionConverter();
    }

    public static StyleConverter<String, IndicatorPosition> getInstance() {
        return Holder.INSTANCE;
    }

    private IndicatorPositionConverter() {
    }

    @Override
    public IndicatorPosition convert(ParsedValue<String, IndicatorPosition> value, Font not_used) {
        String string = value.getValue().toUpperCase();
        try {
            return IndicatorPosition.valueOf(string);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return IndicatorPosition.LEFT;
        }
    }

    @Override
    public String toString() {
        return "IndicatorPositionConverter";
    }

}
