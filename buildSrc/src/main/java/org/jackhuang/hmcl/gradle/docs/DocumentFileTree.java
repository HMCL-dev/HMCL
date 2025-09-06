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
