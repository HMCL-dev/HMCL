/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.Platform;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@JsonSerializable
public record GameJavaVersion(String component, int majorVersion) {
    public static final GameJavaVersion JAVA_25 = new GameJavaVersion("java-runtime-epsilon", 25);
    public static final GameJavaVersion JAVA_21 = new GameJavaVersion("java-runtime-delta", 21);
    public static final GameJavaVersion JAVA_17 = new GameJavaVersion("java-runtime-beta", 17);
    public static final GameJavaVersion JAVA_16 = new GameJavaVersion("java-runtime-alpha", 16);
    public static final GameJavaVersion JAVA_8 = new GameJavaVersion("jre-legacy", 8);

    public static final GameJavaVersion LATEST = JAVA_21;

    public static GameJavaVersion getMinimumJavaVersion(GameVersionNumber gameVersion) {
        if (gameVersion.compareTo("26.1") >= 0)
            return JAVA_25;
        if (gameVersion.compareTo("1.20.5") >= 0)
            return JAVA_21;
        if (gameVersion.compareTo("1.18") >= 0)
            return JAVA_17;
        if (gameVersion.compareTo("1.17") >= 0)
            return JAVA_16;
        if (gameVersion.compareTo("1.13") >= 0)
            return JAVA_8;
        return null;
    }

    public static GameJavaVersion get(int major) {
        return switch (major) {
            case 8 -> JAVA_8;
            case 16 -> JAVA_16;
            case 17 -> JAVA_17;
            case 21 -> JAVA_21;
            case 25 -> JAVA_25;
            default -> null;
        };
    }

    public static List<GameJavaVersion> getSupportedVersions(Platform platform) {
        OperatingSystem operatingSystem = platform.getOperatingSystem();
        Architecture architecture = platform.getArchitecture();
        if (architecture == Architecture.X86) {
            switch (operatingSystem) {
                case WINDOWS:
                    return Arrays.asList(JAVA_8, JAVA_16, JAVA_17);
                case LINUX:
                    return Collections.singletonList(JAVA_8);
            }
        } else if (architecture == Architecture.X86_64) {
            switch (operatingSystem) {
                case WINDOWS:
                case LINUX:
                case MACOS:
                    return Arrays.asList(JAVA_8, JAVA_16, JAVA_17, JAVA_21, JAVA_25);
            }
        } else if (architecture == Architecture.ARM64) {
            switch (operatingSystem) {
                case WINDOWS:
                case MACOS:
                    return Arrays.asList(JAVA_17, JAVA_21, JAVA_25);
            }
        }

        return Collections.emptyList();
    }

    @Override
    public int hashCode() {
        return majorVersion();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof GameJavaVersion that && this.majorVersion == that.majorVersion;
    }
}
