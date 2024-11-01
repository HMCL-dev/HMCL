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

import com.jfoenix.controls.JFXDialog.DialogTransition;
import javafx.css.ParsedValue;
import javafx.css.StyleConverter;
import javafx.scene.text.Font;

/**
 * Converts the CSS for -fx-dialog-transition items into DialogTransition.
 * it's used in JFXDialog.
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
public class DialogTransitionConverter extends StyleConverter<String, DialogTransition> {
    // lazy, thread-safe instatiation
    private static class Holder {
        static final DialogTransitionConverter INSTANCE = new DialogTransitionConverter();
    }

    public static StyleConverter<String, DialogTransition> getInstance() {
        return Holder.INSTANCE;
    }

    private DialogTransitionConverter() {
    }

    @Override
    public DialogTransition convert(ParsedValue<String, DialogTransition> value, Font not_used) {
        String string = value.getValue();
        try {
            return DialogTransition.valueOf(string);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return DialogTransition.CENTER;
        }
    }

    @Override
    public String toString() {
        return "DialogTransitionConverter";
    }
}
