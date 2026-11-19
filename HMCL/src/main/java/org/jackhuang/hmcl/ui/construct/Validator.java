// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.control.TextInputControl;
import org.glavo.m3fx.controls.M3TextInputValidator;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/// Reports a localized error when text fails an application-defined check.
@NotNullByDefault
public class Validator implements M3TextInputValidator {
    /// Message displayed for an invalid value.
    private final String message;
    /// Accepts valid input.
    private final Predicate<String> predicate;

    /// Creates a rule with the given failure message.
    public Validator(String message, Predicate<String> predicate) {
        this.message = message;
        this.predicate = predicate;
    }

    /// Returns the message when the predicate rejects the current text.
    @Override
    public @Nullable String validate(TextInputControl input, String text) {
        return predicate.test(text) ? null : message;
    }
}
