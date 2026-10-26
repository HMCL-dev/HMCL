// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.docs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.TreeMap;

/// @author Glavo
public final class DocumentFileTree {

    public static DocumentFileTree load(Path dir) throws IOException {
        Path documentsDir = dir.toRealPath();
        DocumentFileTree rootTree = new DocumentFileTree();

        Files.walkFileTree(documentsDir, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                String fileName = file.getFileName().toString();
                if (fileName.endsWith(".md")) {
                    DocumentFileTree tree = rootTree.getFileTree(documentsDir.relativize(file.getParent()));
                    if (tree == null)
                        throw new AssertionError();

                    var result = DocumentLocale.parseFileName(fileName.substring(0, fileName.length() - ".md".length()));
                    tree.getFiles().computeIfAbsent(result.name(), name -> new LocalizedDocument(tree, name))
                            .getDocuments()
                            .put(result.locale(), Document.load(tree, file, result.name(), result.locale()));
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return rootTree;
    }

    private final @Nullable DocumentFileTree parent;
    private final TreeMap<String, DocumentFileTree> children = new TreeMap<>();
    private final TreeMap<String, LocalizedDocument> files = new TreeMap<>();

    public DocumentFileTree() {
        this(null);
    }

    public DocumentFileTree(@Nullable DocumentFileTree parent) {
        this.parent = parent;
    }

    @Nullable DocumentFileTree getFileTree(Path relativePath) {
        if (relativePath.isAbsolute())
            throw new IllegalArgumentException(relativePath + " is absolute");

        if (relativePath.getNameCount() == 0)
            throw new IllegalArgumentException(relativePath + " is empty");

        if (relativePath.getNameCount() == 1 && relativePath.getName(0).toString().isEmpty())
            return this;

        DocumentFileTree current = this;
        for (int i = 0; i < relativePath.getNameCount(); i++) {
            String name = relativePath.getName(i).toString();
            if (name.isEmpty())
                throw new IllegalStateException(name + " is empty");
            else if (name.equals("."))
                continue;
            else if (name.equals("..")) {
                current = current.parent;
                if (current == null)
                    return null;
            } else {
                DocumentFileTree finalCurrent = current;
                current = current.children.computeIfAbsent(name, ignored -> new DocumentFileTree(finalCurrent));
            }
        }

        return current;
    }

    public @Nullable DocumentFileTree getParent() {
        return parent;
    }

    public TreeMap<String, DocumentFileTree> getChildren() {
        return children;
    }

    public TreeMap<String, LocalizedDocument> getFiles() {
        return files;
    }
}
