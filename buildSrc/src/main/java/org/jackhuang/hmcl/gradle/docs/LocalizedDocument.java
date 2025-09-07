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
package org.jackhuang.hmcl.gradle.docs;

import java.util.EnumMap;

/// @author Glavo
public final class LocalizedDocument {
    private final DocumentFileTree directory;
    private final String name;
    private final EnumMap<DocumentLocale, Document> documents = new EnumMap<>(DocumentLocale.class);

    public LocalizedDocument(DocumentFileTree directory, String name) {
        this.directory = directory;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public DocumentFileTree getDirectory() {
        return directory;
    }

    public EnumMap<DocumentLocale, Document> getDocuments() {
        return documents;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalizedDocument that
                && this.documents.equals(that.documents);
    }

    @Override
    public int hashCode() {
        return documents.hashCode();
    }

    @Override
    public String toString() {
        return "LocalizedDocument[" +
                "files=" + documents + ']';
    }

}
