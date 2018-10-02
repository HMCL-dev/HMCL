/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util.versioning;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * If a version string contains '-', a {@link ComposedVersionNumber}
 * will be generated.
 *
 * Formats like 1.7.10-OptiFine, 1.12.2-Forge
 *
 * @author huangyuhui
 */
public final class ComposedVersionNumber extends VersionNumber {
    List<VersionNumber> composed;

    public static boolean isComposedVersionNumber(String version) {
        return version.contains("-");
    }

    ComposedVersionNumber(String version) {
        composed = Arrays.stream(version.split("-"))
                .map(VersionNumber::asVersion)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return composed.stream().map(VersionNumber::toString).collect(Collectors.joining("-"));
    }
}
