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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.util.Immutable;

import java.util.Collection;
import java.util.Collections;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class LiteLoaderVersion {
    private final String tweakClass;
    private final String file;
    private final String version;
    private final String md5;
    private final String timestamp;
    private final int lastSuccessfulBuild;
    private final Collection<Library> libraries;

    /**
     * No-arg constructor for Gson.
     */
    @SuppressWarnings("unused")
    public LiteLoaderVersion() {
        this("", "", "", "", "", 0, Collections.emptySet());
    }

    public LiteLoaderVersion(String tweakClass, String file, String version, String md5, String timestamp, int lastSuccessfulBuild, Collection<Library> libraries) {
        this.tweakClass = tweakClass;
        this.file = file;
        this.version = version;
        this.md5 = md5;
        this.timestamp = timestamp;
        this.lastSuccessfulBuild = lastSuccessfulBuild;
        this.libraries = libraries;
    }

    public String getTweakClass() {
        return tweakClass;
    }

    public String getFile() {
        return file;
    }

    public String getVersion() {
        return version;
    }

    public String getMd5() {
        return md5;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public int getLastSuccessfulBuild() {
        return lastSuccessfulBuild;
    }

    public Collection<Library> getLibraries() {
        return Collections.unmodifiableCollection(libraries);
    }
    
}
