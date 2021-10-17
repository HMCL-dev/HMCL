/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.task.Task;

import java.io.File;
import java.nio.charset.Charset;
import java.util.List;

/**
 *
 * @author huangyuhui
 */
public abstract class Modpack {
    private String name;
    private String author;
    private String version;
    private String gameVersion;
    private String description;
    private transient Charset encoding;
    private Object manifest;

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

    public Modpack setName(String name) {
        this.name = name;
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public Modpack setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public Modpack setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Modpack setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Modpack setDescription(String description) {
        this.description = description;
        return this;
    }

    public Charset getEncoding() {
        return encoding;
    }

    public Modpack setEncoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    public Object getManifest() {
        return manifest;
    }

    public Modpack setManifest(Object manifest) {
        this.manifest = manifest;
        return this;
    }

    public abstract Task<?> getInstallTask(DefaultDependencyManager dependencyManager, File zipFile, String name);

    public static boolean acceptFile(String path, List<String> blackList, List<String> whiteList) {
        if (path.isEmpty())
            return true;
        for (String s : blackList)
            if (path.equals(s))
                return false;
        if (whiteList == null || whiteList.isEmpty())
            return true;
        for (String s : whiteList)
            if (path.equals(s))
                return true;
        return false;
    }
}
