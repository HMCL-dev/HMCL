// SPDX-FileCopyrightText: Copyright (C) 2022 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Validates text representing a URL.
@NotNullByDefault
public final class URLValidator extends Validator {
    /// Requires a non-blank value.
    public URLValidator() {
        this(false);
    }

    /// Allows blank input when nullable.
    public URLValidator(boolean nullable) {
        this(i18n("input.url"), nullable);
    }

    /// Displays the supplied message for invalid input.
    public URLValidator(String message, boolean nullable) {
        super(message, text -> StringUtils.isBlank(text) ? nullable : WebURL.tryParseBrowserInput(text) != null);
    }
}
