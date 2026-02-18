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

package com.jfoenix.validation;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.DefaultProperty;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.util.converter.NumberStringConverter;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.ParsePosition;

/**
 * An example of Number field validation, that is applied on text input controls
 * such as {@link TextField} and {@link TextArea}
 *
 * @author Shadi Shaheen
 * @version 1.0
 * @since 2016-03-09
 */
@DefaultProperty(value = "icon")
public class NumberValidator extends ValidatorBase {

    private NumberStringConverter numberStringConverter = new NumberStringConverter(){
        @Override
        public Number fromString(String string) {
            try {
                if (string == null) {
                    return null;
                }
                string = string.trim();
                if (string.length() < 1) {
                    return null;
                }
                // Create and configure the parser to be used
                NumberFormat parser = getNumberFormat();
                ParsePosition parsePosition = new ParsePosition(0);
                Number result = parser.parse(string, parsePosition);
                final int index = parsePosition.getIndex();
                if (index == 0 || index < string.length()) {
                    throw new ParseException("Unparseable number: \"" + string + "\"", parsePosition.getErrorIndex());
                }
                return result;
            } catch (ParseException ex) {
                throw new RuntimeException(ex);
            }
        }
    };

    public NumberValidator() { }

    public NumberValidator(String message) {
        super(message);
    }

    public NumberValidator(NumberStringConverter numberStringConverter) {
        this.numberStringConverter = numberStringConverter;
    }

    public NumberValidator(String message, NumberStringConverter numberStringConverter) {
        super(message);
        this.numberStringConverter = numberStringConverter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = (TextInputControl) srcControl.get();
        String text = textField.getText();
        try {
            hasErrors.set(false);
            if (!text.isEmpty())
                numberStringConverter.fromString(text);
        } catch (Exception e) {
            hasErrors.set(true);
        }
    }

    public NumberStringConverter getNumberStringConverter() {
        return numberStringConverter;
    }

    public void setNumberStringConverter(NumberStringConverter numberStringConverter) {
        this.numberStringConverter = numberStringConverter;
    }
}
