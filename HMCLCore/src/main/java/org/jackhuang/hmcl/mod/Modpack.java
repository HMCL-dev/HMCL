/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import java.nio.charset.Charset;

/**
 *
 * @author huangyuhui
 */
public final class Modpack {
    private final String name;
    private final String author;
    private final String version;
    private final String gameVersion;
    private final String description;
    private final transient Charset encoding;
    private final Object manifest;

    public Modpack() {
        this("", null, null, null, null, null, null);
    }

    public Modpack(String name, String author, String version, String gameVersion, String description, Charset encoding, Object manifest) {
        this.name = name;
        this.author = author;
        this.version = version;
        this.gameVersion = gameVersion;
        this.description = description;
        this.encoding = encoding;
        this.manifest = manifest;
    }

    public String getName() {
        return name;
    }

    public String getAuthor() {
        return author;
    }

    public String getVersion() {
        return version;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Modpack setGameVersion(String gameVersion) {
        return new Modpack(name, author, version, gameVersion, description, encoding, manifest);
    }

    public String getDescription() {
        return description;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public Modpack setEncoding(Charset encoding) {
        return new Modpack(name, author, version, gameVersion, description, encoding, manifest);
    }

    public Object getManifest() {
        return manifest;
    }

    public Modpack setManifest(Object manifest) {
        return new Modpack(name, author, version, gameVersion, description, encoding, manifest);
    }
}
