/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.Immutable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
@Immutable
public final class LibrariesDownloadInfo {

    private final LibraryDownloadInfo artifact;
    private final Map<String, LibraryDownloadInfo> classifiers;

    public LibrariesDownloadInfo(LibraryDownloadInfo artifact) {
        this(artifact, Collections.EMPTY_MAP);
    }

    public LibrariesDownloadInfo(LibraryDownloadInfo artifact, Map<String, LibraryDownloadInfo> classifiers) {
        this.artifact = artifact;
        this.classifiers = new HashMap<>(classifiers);
    }

    public LibraryDownloadInfo getArtifact() {
        return artifact;
    }

    public Map<String, LibraryDownloadInfo> getClassifiers() {
        return classifiers == null ? Collections.EMPTY_MAP : Collections.unmodifiableMap(classifiers);
    }

}
