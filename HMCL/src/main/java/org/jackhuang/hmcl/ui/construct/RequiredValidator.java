// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Requires non-blank input in a Material text field.
@NotNullByDefault
public final class RequiredValidator extends Validator {

    /// Uses the localized required-field message.
    public RequiredValidator() {
        this(i18n("input.not_empty"));
    }

    /// Uses the supplied validation message.
    public RequiredValidator(String message) {
        super(message, StringUtils::isNotBlank);
    }
}
