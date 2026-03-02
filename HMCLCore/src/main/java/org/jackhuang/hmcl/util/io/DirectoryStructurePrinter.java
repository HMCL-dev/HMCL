/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

package org.jackhuang.hmcl.util.io;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

final class DirectoryStructurePrinter {
    private DirectoryStructurePrinter() {
    }

    public static String list(Path path, int maxDepth) throws IOException {
        StringBuilder output = new StringBuilder(128);
        list(path, maxDepth, output);
        output.setLength(output.length() - 1);
        return output.toString();
    }

    private static void list(Path path, int maxDepth, StringBuilder output) throws IOException {
        output.append("Filesystem structure of: ").append(path).append('\n');

        if (!Files.exists(path)) {
            pushMessage(output, "nonexistent path", 1);
            return;
        }
        if (Files.isRegularFile(path)) {
            pushMessage(output, "regular file path", 1);
            return;
        }
        if (Files.isDirectory(path)) {
            Files.walkFileTree(path, new FileVisitor<Path>() {
                private boolean isFolderEmpty;

                private int depth = 1;

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    isFolderEmpty = true;

                    pushFile(output, dir, depth);
                    if (depth == maxDepth) {
                        pushMessage(output, "too deep", depth);
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    depth++;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    isFolderEmpty = false;

                    pushFile(output, file, depth);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    visitFile(file, null);

                    pushMessage(output, exc.toString(), depth);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                    if (isFolderEmpty) {
                        pushMessage(output, "empty directory", depth);
                    }

                    depth--;
                    return FileVisitResult.CONTINUE;
                }
            });
            return;
        }

        pushMessage(output, "unknown file type", 1);
    }

    private static void pushFile(StringBuilder output, Path file, int depth) {
        output.append("|");
        for (int i = 1; i < depth; i++) {
            output.append("  |");
        }
        output.append("-> ").append(FileUtils.getName(file)).append('\n');
    }

    private static void pushMessage(StringBuilder output, String message, int depth) {
        output.append("| ");
        for (int i = 1; i < depth; i++) {
            output.append(" | ");
        }
        output.append('<').append(message).append(">\n");
    }
}
