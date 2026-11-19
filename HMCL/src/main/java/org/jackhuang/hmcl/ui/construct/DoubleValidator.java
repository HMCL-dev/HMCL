// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Validates text representing a floating-point number.
@NotNullByDefault
public final class DoubleValidator extends Validator {
    /// Requires a non-blank value.
    public DoubleValidator() {
        this(false);
    }

    /// Allows blank input when nullable.
    public DoubleValidator(boolean nullable) {
        this(i18n("input.number"), nullable);
    }

    /// Displays the supplied message for invalid input.
    public DoubleValidator(String message, boolean nullable) {
        super(message, text -> StringUtils.isBlank(text) ? nullable : Lang.toDoubleOrNull(text) != null);
    }
}
