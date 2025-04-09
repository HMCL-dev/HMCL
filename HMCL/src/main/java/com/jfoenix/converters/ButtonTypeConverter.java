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

import com.jfoenix.controls.JFXButton.ButtonType;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * Converts the CSS for -fx-button-type items into ButtonType.
 * it's used in JFXButton
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class ButtonTypeConverter extends StyleConverter<String, ButtonType> {

    private ButtonTypeConverter() {
        super();
    }

    // lazy, thread-safe instantiation
    private static class Holder {
        static final ButtonTypeConverter INSTANCE = new ButtonTypeConverter();

        private Holder() {
            throw new IllegalAccessError("Holder class");
        }
    }

    public static StyleConverter<String, ButtonType> getInstance() {
        return Holder.INSTANCE;
    }


    @Override
    public ButtonType convert(ParsedValue<String, ButtonType> value, Font notUsedFont) {
        String string = value.getValue();
        try {
            return ButtonType.valueOf(string);
        } catch (IllegalArgumentException | NullPointerException exception) {
            LOG.warning("Invalid button type value '" + string + "'");
            return ButtonType.FLAT;
        }
    }

    @Override
    public String toString() {
        return "ButtonTypeConverter";
    }
}
