// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.beans.NamedArg;
import javafx.scene.control.TextInputControl;
import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.StringUtils;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class URLValidator extends ValidatorBase {
    private final boolean nullable;

    public URLValidator() {
        this(false);
    }

    public URLValidator(@NamedArg("nullable") boolean nullable) {
        this(i18n("input.url"), nullable);
    }

    public URLValidator(@NamedArg("message") String message, @NamedArg("nullable") boolean nullable) {
        super(message);
        this.nullable = nullable;
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = ((TextInputControl) srcControl.get());

        if (StringUtils.isBlank(textField.getText()))
            hasErrors.set(!nullable);
        else {
            hasErrors.set(WebURL.tryParseBrowserInput(textField.getText()) == null);
        }
    }
}
