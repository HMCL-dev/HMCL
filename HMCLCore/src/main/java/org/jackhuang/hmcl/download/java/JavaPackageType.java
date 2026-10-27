// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.download.java;

/**
 * @author Glavo
 */
public enum JavaPackageType {
    JRE("JRE", false, false),
    JDK("JDK", true, false),
    JREFX("JRE FX", false, true),
    JDKFX("JDK FX", true, true);

    private final String displayName;
    private final boolean jdk;
    private final boolean javafx;

    JavaPackageType(String displayName, boolean jdk, boolean javafx) {
        this.displayName = displayName;
        this.jdk = jdk;
        this.javafx = javafx;
    }

    public static JavaPackageType of(boolean jdk, boolean javafx) {
        if (jdk)
            return javafx ? JDKFX : JDK;
        else
            return javafx ? JREFX : JRE;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isJDK() {
        return jdk;
    }

    public boolean isJavaFXBundled() {
        return javafx;
    }
}
