/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
