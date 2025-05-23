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

/**
 * @author Glavo
 */
public enum JavaFXVersionType {
    CLASSIC("classic", 11),
    MODERN("modern", 22);

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
