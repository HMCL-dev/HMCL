/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.auth.authlibinjector;

import java.nio.file.Path;

public class AuthlibInjectorArtifactInfo {

    private int buildNumber;
    private String version;
    private Path location;

    public AuthlibInjectorArtifactInfo(int buildNumber, String version, Path location) {
        this.buildNumber = buildNumber;
        this.version = version;
        this.location = location;
    }

    public int getBuildNumber() {
        return buildNumber;
    }

    public String getVersion() {
        return version;
    }

    public Path getLocation() {
        return location;
    }

    @Override
    public String toString() {
        return "authlib-injector [buildNumber=" + buildNumber + ", version=" + version + "]";
    }
}
