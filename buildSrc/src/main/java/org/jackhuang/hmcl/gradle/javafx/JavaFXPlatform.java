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
package org.jackhuang.hmcl.gradle.javafx;

import java.util.*;

/**
 * @author Glavo
 */
public final class JavaFXPlatform {

    public static final String LEGACY_JAVAFX_VERSION = "19.0.2.1";
    public static final String CLASSIC_JAVAFX_VERSION = "21.0.8";
    public static final String MODERN_JAVAFX_VERSION = "25";

    private static final String OFFICIAL_GROUP_ID = "org.openjfx";
    private static final String GLAVO_GROUP_ID = "org.glavo.hmcl.openjfx";

    public static final Map<String, JavaFXPlatform> ALL = new LinkedHashMap<>();

    static {
        Map<JavaFXVersionType, String> legacyVersions = Map.of(JavaFXVersionType.CLASSIC, LEGACY_JAVAFX_VERSION);

        // Windows
        ALL.put("windows-x86", new JavaFXPlatform("win-x86", legacyVersions));
        ALL.put("windows-x86_64", new JavaFXPlatform("win"));
        ALL.put("windows-arm64", new JavaFXPlatform("win", GLAVO_GROUP_ID, "18.0.2+1-arm64"));

        // macOS
        ALL.put("macos-x86_64", new JavaFXPlatform("mac"));
        ALL.put("macos-arm64", new JavaFXPlatform("mac-aarch64"));

        // Linux
        ALL.put("linux-x86_64", new JavaFXPlatform("linux"));
        ALL.put("linux-arm32", new JavaFXPlatform("linux-arm32-monocle", legacyVersions));
        ALL.put("linux-arm64", new JavaFXPlatform("linux-aarch64", Map.of(
                JavaFXVersionType.CLASSIC, "21.0.1",
                JavaFXVersionType.MODERN, MODERN_JAVAFX_VERSION
        )));
        ALL.put("linux-loongarch64", new JavaFXPlatform("linux", GLAVO_GROUP_ID, "17.0.8-loongarch64"));
        ALL.put("linux-loongarch64_ow", new JavaFXPlatform("linux", GLAVO_GROUP_ID, "19-ea+10-loongson64"));
        ALL.put("linux-riscv64", new JavaFXPlatform("linux", GLAVO_GROUP_ID, "19.0.2.1-riscv64"));

        // FreeBSD
        ALL.put("freebsd-x86_64", new JavaFXPlatform("freebsd", GLAVO_GROUP_ID, "14.0.2.1-freebsd"));
    }

    private final String classifier;
    private final String groupId;
    private final SortedMap<JavaFXVersionType, String> versions;

    public JavaFXPlatform(String classifier) {
        this(classifier, OFFICIAL_GROUP_ID, Map.of(
                JavaFXVersionType.CLASSIC, CLASSIC_JAVAFX_VERSION,
                JavaFXVersionType.MODERN, MODERN_JAVAFX_VERSION
        ));
    }

    public JavaFXPlatform(String classifier, Map<JavaFXVersionType, String> versions) {
        this(classifier, OFFICIAL_GROUP_ID, versions);
    }

    public JavaFXPlatform(String classifier, String groupId, String version) {
        this(classifier, groupId, Map.of(JavaFXVersionType.CLASSIC, version));
    }

    public JavaFXPlatform(String classifier, String groupId, Map<JavaFXVersionType, String> versions) {
        this.classifier = classifier;
        this.groupId = groupId;
        this.versions = Collections.unmodifiableSortedMap(new TreeMap<>(versions));
    }

    public String getClassifier() {
        return classifier;
    }

    public String getGroupId() {
        return groupId;
    }

    public SortedMap<JavaFXVersionType, String> getVersions() {
        return versions;
    }
}
