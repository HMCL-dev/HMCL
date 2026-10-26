// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.ci;

import java.util.Objects;

/// @author Glavo
public final class GitHubActionUtils {
    private static final String OFFICIAL_ORGANIZATION = "HMCL-dev";

    public static final boolean IS_ON_OFFICIAL_REPO =
            OFFICIAL_ORGANIZATION.equalsIgnoreCase(System.getenv("GITHUB_REPOSITORY_OWNER"))
                    && Objects.requireNonNullElse(System.getenv("GITHUB_BASE_REF"), "").isBlank();

    private GitHubActionUtils() {
    }
}
