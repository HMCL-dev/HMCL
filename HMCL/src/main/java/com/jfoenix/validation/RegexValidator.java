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

import java.util.regex.Pattern;


/**
 * Regex validation, that is applied on text input controls
 * such as {@link TextField} and {@link TextArea}.
 *
 * @version 1.0
 * @since 2018-08-06
 */
@DefaultProperty(value = "icon")
public class RegexValidator extends ValidatorBase {

    private String regexPattern;

    public RegexValidator(String message) {
        super(message);
    }

    public RegexValidator() {

    }


    private Pattern regexPatternCompiled;

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
        String text = (textField.getText() == null) ? "" : textField.getText(); // Treat null like empty string

        if (regexPatternCompiled.matcher(text).matches()) {
            hasErrors.set(false);
        } else {
            hasErrors.set(true);
        }
    }

    /*
     * GETTER AND SETTER
     */
    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
        this.regexPatternCompiled = Pattern.compile(regexPattern);
    }

    public String getRegexPattern() {
        return regexPattern;
    }
}
