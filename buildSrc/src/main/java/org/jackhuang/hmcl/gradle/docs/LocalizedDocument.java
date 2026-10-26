// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
