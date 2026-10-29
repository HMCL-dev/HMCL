// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

@NotNullByDefault
public record AddonLoader(String name, @Nullable AddonLoaderType type) {
    public static boolean mightBeLoader(String str) {
        if (StringUtils.isBlank(str)
                || !StringUtils.isASCII(str)
                || "client".equalsIgnoreCase(str) || "server".equalsIgnoreCase(str))
            return false;
        int l = str.length();
        for (int i = 0; i < l; i++) {
            char c = str.charAt(i);
            if (c != '-' && c != ' ' && c != '_' && !StringUtils.isAlphabetic(c)) return false;
        }
        return true;
    }

    public static AddonLoader of(String name) {
        for (var type : ModLoaderType.values()) {
            for (String loaderName : type.names()) {
                if (name.equalsIgnoreCase(loaderName)) {
                    return new AddonLoader(name, type);
                }
            }

        }
        return new AddonLoader(name, null);
    }

}
