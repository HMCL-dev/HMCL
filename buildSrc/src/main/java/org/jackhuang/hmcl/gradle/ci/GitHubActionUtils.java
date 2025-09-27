/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
