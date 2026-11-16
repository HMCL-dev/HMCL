// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.NamedArg;
import javafx.scene.control.TextInputControl;

import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RequiredValidator extends ValidatorBase {

    public RequiredValidator() {
        this(i18n("input.not_empty"));
    }

    public RequiredValidator(@NamedArg("message") String message) {
        super(message);
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = ((TextInputControl) srcControl.get());

        hasErrors.set(StringUtils.isBlank(textField.getText()));
    }
}
