// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;

import java.util.Locale;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Supplies the icon and localized default title for messages and inline hints.
@NotNullByDefault
public enum MessageType {
    /// A failed operation.
    ERROR(SVG.ERROR),
    /// General information.
    INFO(SVG.INFO),
    /// A condition requiring attention.
    WARNING(SVG.WARNING),
    /// A request for a decision.
    QUESTION(SVG.HELP),
    /// A completed operation.
    SUCCESS(SVG.CHECK_CIRCLE);

    /// The message icon.
    private final SVG icon;

    /// Associates a message type with its icon.
    MessageType(SVG icon) {
        this.icon = icon;
    }

    /// Returns the icon used by this message type.
    public SVG getIcon() {
        return icon;
    }

    /// Returns the localized default message title.
    public String getDisplayName() {
        return i18n("message." + name().toLowerCase(Locale.ROOT));
    }
}
