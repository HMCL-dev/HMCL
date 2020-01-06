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
package org.jackhuang.hmcl.util.io;


import org.jackhuang.hmcl.util.function.ExceptionalPredicate;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Non thread-safe
 *
 * @author huangyuhui
 */
public final class Zipper implements Closeable {

    private final FileSystem fs;

    public Zipper(Path zipFile) throws IOException {
        this(zipFile, null);
    }

    public Zipper(Path zipFile, Charset encoding) throws IOException {
        Files.deleteIfExists(zipFile);
        fs = CompressingUtils.createWritableZipFileSystem(zipFile, encoding);
    }

    @Override
    public void close() throws IOException {
        fs.close();
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source  the file in basePath to be compressed
     * @param rootDir the path of the directory in this zip file.
     */
    public void putDirectory(Path source, String rootDir) throws IOException {
        putDirectory(source, rootDir, null);
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source  the file in basePath to be compressed
     * @param targetDir the path of the directory in this zip file.
     * @param filter  returns false if you do not want that file or directory
     */
    public void putDirectory(Path source, String targetDir, ExceptionalPredicate<String, IOException> filter) throws IOException {
        Path root = fs.getPath(targetDir);
        Files.createDirectories(root);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (".DS_Store".equals(file.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String relativePath = source.relativize(file).normalize().toString();
                if (filter != null && !filter.test(relativePath.replace('\\', '/'))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.copy(file, root.resolve(relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String relativePath = source.relativize(dir).normalize().toString();
                if (filter != null && !filter.test(relativePath.replace('\\', '/'))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Path path = root.resolve(relativePath);
                if (Files.notExists(path)) {
                    Files.createDirectory(path);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void putFile(File file, String path) throws IOException {
        putFile(file.toPath(), path);
    }

    public void putFile(Path file, String path) throws IOException {
        Files.copy(file, fs.getPath(path));
    }

    public void putStream(InputStream in, String path) throws IOException {
        Files.copy(in, fs.getPath(path));
    }

    public void putTextFile(String text, String path) throws IOException {
        putTextFile(text, "UTF-8", path);
    }

    public void putTextFile(String text, String encoding, String pathName) throws IOException {
        Files.write(fs.getPath(pathName), text.getBytes(encoding));
    }

}
