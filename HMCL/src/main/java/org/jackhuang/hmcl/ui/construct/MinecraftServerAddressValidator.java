// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.validation.base.ValidatorBase;
import javafx.scene.control.TextInputControl;
import org.jackhuang.hmcl.util.ServerAddress;

import java.util.Objects;

public class MinecraftServerAddressValidator extends ValidatorBase {
    private final boolean ignoreEmpty;

    public MinecraftServerAddressValidator(String message, boolean ignoreEmpty) {
        super(message);
        this.ignoreEmpty = ignoreEmpty;
    }

    @Override
    protected void eval() {
        if (srcControl.get() instanceof TextInputControl) {
            evalTextInputField();
        }
    }

    private void evalTextInputField() {
        TextInputControl textField = ((TextInputControl) srcControl.get());
        String text = textField.getText();

        if (text == null || text.isEmpty()) {
            if (ignoreEmpty) {
                hasErrors.set(false);
                return;
            }
        }

        try {
            ServerAddress.parse(Objects.requireNonNull(text));
            hasErrors.set(false);
        } catch (Exception e) {
            hasErrors.set(true);
        }
    }
}
