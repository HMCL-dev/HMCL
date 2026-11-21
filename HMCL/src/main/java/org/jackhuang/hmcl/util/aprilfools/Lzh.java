// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.aprilfools;

import org.jackhuang.hmcl.util.Lazy;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.i18n.SupportedLocale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Lzh {

    private static final Lazy<SupportedLocale> lzhLocale = new Lazy<>(() -> {
        if (!I18n.getLocale().getLocale().getLanguage().equals("zh"))
            return null;

        SupportedLocale lzh = SupportedLocale.getSupportedLocales().stream()
                .filter(locale -> "lzh".equals(locale.getName()))
                .findFirst().orElse(null);

        if (lzh == null) LOG.warning("No supported locale found for lzh");

        return lzh;
    });

    public static boolean shouldPrompt() {
        return lzhLocale.get() != null;
    }

    public static SupportedLocale getLzhLocale() {
        return lzhLocale.get();
    }

    private Lzh() {
    }
}
