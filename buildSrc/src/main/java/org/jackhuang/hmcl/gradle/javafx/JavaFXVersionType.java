// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.javafx;

/**
 * @author Glavo
 */
public enum JavaFXVersionType {
    CLASSIC("classic", 17),
    MODERN("modern", 23);

    private final String name;
    private final int javaVersion;

    JavaFXVersionType(String name, int javaVersion) {
        this.name = name;
        this.javaVersion = javaVersion;
    }

    public String getName() {
        return name;
    }

    public int getJavaVersion() {
        return javaVersion;
    }
}
