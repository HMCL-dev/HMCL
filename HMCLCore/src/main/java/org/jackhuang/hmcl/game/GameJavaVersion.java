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

public enum GameJavaVersion {
    JAVA_8("jre-legacy", 8),
    JAVA_16("java-runtime-alpha", 16),
    JAVA_17("java-runtime-beta", 17),
    JAVA_21("java-runtime-delta", 21)
    ;
    private final String component;
    private final int majorVersion;

    private GameJavaVersion(String component, int majorVersion) {
        this.component = component;
        this.majorVersion = majorVersion;
    }

    public String getComponent() {
        return component;
    }

    public int getMajorVersion() {
        return majorVersion;
    }
}
