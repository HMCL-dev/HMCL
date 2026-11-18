// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.java.JavaInfo;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

public final class Base64Utils {
    // CVE-2026-114938
    public static boolean shouldDisableBase64Intrinsics(JavaInfo javaInfo) {
        if (javaInfo.getPlatform().arch() != Architecture.X86_64)
            return false;

        int javaFeatureVersion = javaInfo.getParsedVersion();
        VersionNumber javaVersion = javaInfo.getVersionNumber();
        return switch (javaFeatureVersion) {
            case 18, 19, 20, 22, 23, 24, 26 -> true;
            case 17 -> javaVersion.compareTo("17.0.6") >= 0 && javaVersion.compareTo("17.0.21.1 ") < 0;
            case 21 -> javaVersion.compareTo("21.0.13.1") < 0;
            case 25 -> javaVersion.compareTo("25.0.5.1") < 0;
            case 27 -> javaVersion.compareTo("27.0.1.1") < 0;
            default -> false;
        };
    }

    private Base64Utils() {
    }
}
