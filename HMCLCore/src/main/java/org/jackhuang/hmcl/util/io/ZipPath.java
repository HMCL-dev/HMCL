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

import org.jackhuang.hmcl.util.Lang;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class ZipPath implements Path {

    private final ZipFileSystem zfs;
    private final List<String> path;
    private List<String> normalized;
    private final boolean absolute;

    ZipPath(ZipFileSystem zfs, String path) {
        this(zfs, getPathComponents(path), path.startsWith("/"));
    }

    ZipPath(ZipFileSystem zfs, List<String> path, boolean absolute) {
        this.zfs = zfs;
        this.path = path;
        this.absolute = absolute;
    }

    @NotNull
    @Override
    public ZipFileSystem getFileSystem() {
        return zfs;
    }

    @Override
    public boolean isAbsolute() {
        return absolute;
    }

    @Override
    public ZipPath getRoot() {
        if (this.isAbsolute())
            return zfs.rootDir;
        else
            return null;
    }

    @Override
    public ZipPath getFileName() {
        if (path.size() <= 1) return this;
        else return new ZipPath(zfs, Collections.singletonList(path.get(path.size() - 1)), false);
    }

    @Override
    public ZipPath getParent() {
        if (path.isEmpty()) return null;
        else if (path.size() == 1) return getRoot();
        else return new ZipPath(zfs, path.subList(0, path.size() - 1), absolute);
    }

    @Override
    public int getNameCount() {
        return path.size();
    }

    @NotNull
    @Override
    public ZipPath getName(int index) {
        if (index < 0 || index >= path.size()) throw new IllegalArgumentException();
        return new ZipPath(zfs, Collections.singletonList(path.get(index)), false);
    }

    @NotNull
    @Override
    public ZipPath subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || beginIndex >= path.size() || endIndex > path.size() || beginIndex >= endIndex) {
            throw new IllegalArgumentException();
        }

        return new ZipPath(zfs, path.subList(beginIndex, endIndex), absolute);
    }

    @Override
    public boolean startsWith(@NotNull Path other) {
        ZipPath p1 = this;
        ZipPath p2 = ensurePath(other);
        if (p1.isAbsolute() != p2.isAbsolute() || p1.path.size() < p2.path.size()) {
            return false;
        }
        int length = p2.path.size();
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(p1.path.get(i), p2.path.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean startsWith(@NotNull String other) {
        return startsWith(getFileSystem().getPath(other));
    }

    @Override
    public boolean endsWith(@NotNull Path other) {
        ZipPath p1 = this;
        ZipPath p2 = ensurePath(other);

        if (p2.isAbsolute() && !p1.isAbsolute() ||
                p2.isAbsolute() && p1.isAbsolute() && p1.path.size() != p2.path.size() ||
                p1.path.size() < p2.path.size()
        ) {
            return false;
        }

        int length = p2.path.size();
        for (int i = 0; i < length; i++) {
            if (!Objects.equals(p1.path.get(p1.path.size() - i - 1), p2.path.get(p2.path.size() - i - 1))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean endsWith(@NotNull String other) {
        return endsWith(getFileSystem().getPath(other));
    }

    @NotNull
    @Override
    public ZipPath normalize() {
        if (isNormalizable()) {
            doNormalize();
            return new ZipPath(zfs, normalized, absolute);
        }
        return this;
    }

    private boolean isNormalizable() {
        for (String component : path) {
            if (".".equals(component) || "..".equals(component)) {
                return true;
            }
        }
        return false;
    }

    private void doNormalize() {
        if (normalized != null) return;
        Stack<String> stack = new Stack<>();
        for (String component : path) {
            if (".".equals(component)) {
                continue;
            } else if ("..".equals(component)) {
                if (!stack.isEmpty()) stack.pop();
            } else {
                stack.push(component);
            }
        }
        normalized = new ArrayList<>(stack);
    }

    @NotNull
    @Override
    public ZipPath resolve(@NotNull Path other) {
        ZipPath p1 = this;
        ZipPath p2 = ensurePath(other);
        if (p2.isAbsolute()) return p2;
        return new ZipPath(zfs, Lang.merge(p1.path, p2.path), absolute);
    }

    @NotNull
    @Override
    public ZipPath resolve(@NotNull String other) {
        return resolve(getFileSystem().getPath(other));
    }

    @NotNull
    @Override
    public ZipPath resolveSibling(@NotNull Path other) {
        ZipPath parent = getParent();
        return parent == null ? ensurePath(other) : parent.resolve(other);
    }

    @NotNull
    @Override
    public ZipPath resolveSibling(@NotNull String other) {
        return resolveSibling(zfs.getPath(other));
    }

    @NotNull
    @Override
    public Path relativize(@NotNull Path other) {
        ZipPath p1 = this;
        ZipPath p2 = ensurePath(other);

        if (p2.equals(p1)) return new ZipPath(zfs, Collections.emptyList(), false);
        if (p1.isAbsolute() != p2.isAbsolute()) throw new IllegalArgumentException();

        int l = Math.min(p1.path.size(), p2.path.size());
        int common = 0;
        while (common < l && Objects.equals(p1.path.get(common), p2.path.get(common))) common++;
        int up = p1.path.size() - common;
        List<String> result = new ArrayList<>();
        for (int i = 0; i < up; i++) result.add("..");
        result.addAll(p2.path);
        return new ZipPath(zfs, result, false);
    }

    @NotNull
    @Override
    public URI toUri() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ZipPath toAbsolutePath() {
        if (isAbsolute()) {
            return this;
        }

        return new ZipPath(zfs, path, true);
    }

    @NotNull
    @Override
    public ZipPath toRealPath(@NotNull LinkOption... options) throws IOException {
        ZipPath absolute = toAbsolutePath().normalize();
        absolute.checkAccess();
        return absolute;
    }

    void checkAccess(AccessMode... modes) throws IOException {
        boolean w = false;
        boolean x = false;
        for (AccessMode mode : modes) {
            switch (mode) {
                case READ:
                    break;
                case WRITE:
                    w = true;
                    break;
                case EXECUTE:
                    x = true;
                    break;
                default:
                    throw new UnsupportedOperationException();
            }
        }
        zfs.checkAccess(toAbsolutePath().normalize());
        if ((w && zfs.isReadOnly()) || x) {
            throw new AccessDeniedException(toString());
        }
    }

    @NotNull
    @Override
    public File toFile() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public WatchKey register(@NotNull WatchService watcher, @NotNull WatchEvent.Kind<?> @NotNull [] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public WatchKey register(@NotNull WatchService watcher, @NotNull WatchEvent.Kind<?> @NotNull ... events) throws IOException {
        throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Iterator<Path> iterator() {
        return new Iterator<Path>() {
            private int index = 0;

            public boolean hasNext() {
                return index < getNameCount();
            }

            public Path next() {
                if (index < getNameCount()) {
                    return getName(index++);
                }
                throw new NoSuchElementException();
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZipPath paths = (ZipPath) o;
        return absolute == paths.absolute && path.equals(paths.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, absolute);
    }

    @Override
    public int compareTo(@NotNull Path other) {
        ZipPath p1 = this;
        ZipPath p2 = ensurePath(other);
        return p1.toString().compareTo(p2.toString());
    }

    ZipFileAttributes getAttributes() throws IOException {
        ZipFileAttributes attributes = zfs.readAttributes(this);
        if (attributes == null) throw new NoSuchFileException(toString());
        else return attributes;
    }

    static List<String> getPathComponents(String path) {
        List<String> components = new ArrayList<>();
        int lastSlash = 0;
        for (int i = 0; i <= path.length(); i++) {
            if (i == path.length() || path.charAt(i) == '/' || path.charAt(i) == '\\') {
                if (i != lastSlash) {
                    String component = path.substring(lastSlash, i);
                    components.add(component);
                }

                lastSlash = i + 1;
            }
        }
        return components;
    }

    private static String normalizePath(String path) {
        return String.join("/", getPathComponents(path));
    }

    static ZipPath ensurePath(Path path) {
        if (path == null) throw new NullPointerException();
        if (!(path instanceof ZipPath)) throw new ProviderMismatchException();
        return (ZipPath) path;
    }

    String getEntryName() {
        if (!isAbsolute()) throw new IllegalStateException();
        return String.join("/", path);
    }

    @Override
    public String toString() {
        String str = String.join("/", path);
        if (absolute) return "/" + str;
        else return str;
    }
}
