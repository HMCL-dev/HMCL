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
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

/**
 * Non thread-safe
 *
 * @author huangyuhui
 */
public final class Zipper implements Closeable {

    private final ZipOutputStream zos;
    private final byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];

    public Zipper(Path zipFile) throws IOException {
        this(zipFile, StandardCharsets.UTF_8);
    }

    public Zipper(Path zipFile, Charset encoding) throws IOException {
        this.zos = new ZipOutputStream(Files.newOutputStream(zipFile), encoding);
    }

    private static String normalize(String path) {
        path = path.replace('\\', '/');
        if (path.startsWith("/"))
            path = path.substring(1);
        if (path.endsWith("/"))
            path = path.substring(0, path.length() - 1);
        return path;
    }

    private static String resolve(String dir, String file) {
        if (dir.isEmpty()) return file;
        if (file.isEmpty()) return dir;
        return dir + "/" + file;
    }

    @Override
    public void close() throws IOException {
        zos.close();
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source  the file in basePath to be compressed
     * @param targetDir the path of the directory in this zip file.
     */
    public void putDirectory(Path source, String targetDir) throws IOException {
        putDirectory(source, targetDir, null);
    }

    /**
     * Compress all the files in sourceDir
     *
     * @param source  the file in basePath to be compressed
     * @param targetDir the path of the directory in this zip file.
     * @param filter  returns false if you do not want that file or directory
     */
    public void putDirectory(Path source, String targetDir, ExceptionalPredicate<String, IOException> filter) throws IOException {
        String root = normalize(targetDir);
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (".DS_Store".equals(file.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                String relativePath = normalize(source.relativize(file).normalize().toString());
                if (filter != null && !filter.test(relativePath)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                putFile(file, resolve(root, relativePath));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String relativePath = normalize(source.relativize(dir).normalize().toString());
                if (filter != null && !filter.test(relativePath)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                try {
                    zos.putNextEntry(new ZipEntry(resolve(root, relativePath) + "/"));
                    zos.closeEntry();
                } catch (ZipException ignored) {
                    // Directory already exists
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public void putFile(File file, String path) throws IOException {
        putFile(file.toPath(), path);
    }

    public void putFile(Path file, String path) throws IOException {
        path = normalize(path);

        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);

        ZipEntry entry = new ZipEntry(attrs.isDirectory() ? path + "/" : path);
        entry.setCreationTime(attrs.creationTime());
        entry.setLastAccessTime(attrs.lastAccessTime());
        entry.setLastModifiedTime(attrs.lastModifiedTime());

        if (attrs.isDirectory()) {
            try {
                zos.putNextEntry(entry);
                zos.closeEntry();
            } catch (ZipException ignored) {
                // Directory already exists
            }
        } else {
            try (InputStream input = Files.newInputStream(file)) {
                zos.putNextEntry(entry);
                IOUtils.copyTo(input, zos, buffer);
                zos.closeEntry();
            }
        }
    }

    public void putStream(InputStream in, String path) throws IOException {
        zos.putNextEntry(new ZipEntry(normalize(path)));
        IOUtils.copyTo(in, zos, buffer);
        zos.closeEntry();
    }

    public void putTextFile(String text, String path) throws IOException {
        putTextFile(text, StandardCharsets.UTF_8, path);
    }

    public void putTextFile(String text, Charset encoding, String path) throws IOException {
        zos.putNextEntry(new ZipEntry(normalize(path)));
        zos.write(text.getBytes(encoding));
        zos.closeEntry();
    }
}
