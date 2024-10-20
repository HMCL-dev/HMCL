/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.java;

/**
 * @author Glavo
 */
public enum JavaPackageType {
    JRE(false, false),
    JDK(true, false),
    JREFX(false, true),
    JDKFX(true, true);

    private final boolean jdk;
    private final boolean javafx;

    JavaPackageType(boolean jdk, boolean javafx) {
        this.jdk = jdk;
        this.javafx = javafx;
    }

    public static JavaPackageType of(boolean jdk, boolean javafx) {
        if (jdk)
            return javafx ? JDKFX : JDK;
        else
            return javafx ? JREFX : JRE;
    }

    public boolean isJDK() {
        return jdk;
    }

    public boolean isJavaFXBundled() {
        return javafx;
    }
}
