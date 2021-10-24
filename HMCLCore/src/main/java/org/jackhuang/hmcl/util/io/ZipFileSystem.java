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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.Lang.toIterable;
import static org.jackhuang.hmcl.util.io.ZipPath.getPathComponents;

public class ZipFileSystem extends FileSystem {

    private final ZipFileSystemProvider provider;
    private final ZipFile zipFile;
    private final boolean readOnly;
    private final IndexNode root;
    private final Map<String, IndexNode> entries = new HashMap<>();
    final ZipPath rootDir;

    private volatile boolean isOpen = true;

    public ZipFileSystem(ZipFileSystemProvider provider, ZipFile zipFile, boolean readOnly) {
        this.provider = provider;
        this.zipFile = zipFile;
        this.readOnly = readOnly;

        this.root = new IndexNode(null, true, "");
        this.rootDir = new ZipPath(this, "/");

        buildTree();
    }

    @Override
    public FileSystemProvider provider() {
        return provider;
    }

    @Override
    public void close() throws IOException {
        isOpen = false;
        zipFile.close();
    }

    @Override
    public boolean isOpen() {
        return isOpen;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public String getSeparator() {
        return "/";
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return Collections.singleton(rootDir);
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return null;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return null;
    }

    @NotNull
    @Override
    public Path getPath(@NotNull String first, @NotNull String @NotNull ... more) {
        StringBuilder sb = new StringBuilder(first);
        for (String segment : more) {
            if (segment.length() > 0) {
                if (sb.length() > 0) {
                    sb.append('/');
                }
                sb.append(segment);
            }
        }
        return new ZipPath(this, sb.toString());
    }

    ZipFileAttributes readAttributes(ZipPath path) {
        ensureOpen();

        Optional<IndexNode> inode = getInode(path);
        if (!inode.isPresent()) return null;
        return inode.get().getAttributes();
    }

    InputStream newInputStream(ZipPath path, OpenOption... options) throws IOException {
        ensureOpen();

        ZipPath realPath = path.toRealPath();
        ZipArchiveEntry entry = zipFile.getEntry(realPath.getEntryName());
        return zipFile.getInputStream(entry);
    }

    DirectoryStream<Path> newDirectoryStream(ZipPath dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        Optional<IndexNode> inode = getInode(dir);
        if (!inode.isPresent() || !inode.get().isDirectory()) throw new NotDirectoryException(dir.toString());

        List<ZipPath> list = new ArrayList<>();
        for (IndexNode child = inode.get().child; child != null; child = child.sibling) {
            list.add(new ZipPath(this, child.name));
        }

        return new DirectoryStream<Path>() {
            private volatile boolean isClosed = false;
            private volatile Iterator<ZipPath> itr;

            @Override
            public synchronized Iterator<Path> iterator() {
                if (isClosed)
                    throw new ClosedDirectoryStreamException();
                if (itr != null)
                    throw new IllegalStateException("Iterator has already been returned");
                itr = list.iterator();

                return new Iterator<Path>() {
                    @Override
                    public boolean hasNext() {
                        if (isClosed) return false;
                        return itr.hasNext();
                    }

                    @Override
                    public Path next() {
                        if (isClosed) throw new NoSuchElementException();
                        return itr.next();
                    }
                };
            }

            @Override
            public synchronized void close() {
                isClosed = true;
            }
        };
    }

    void checkAccess(ZipPath path) throws IOException {
        ensureOpen();

        if (!getInode(path.getEntryName()).isPresent()) {
            throw new NoSuchFileException(path.toString());
        }
    }

    private static final String GLOB_SYNTAX = "glob";
    private static final String REGEX_SYNTAX = "regex";

    @Override
    public PathMatcher getPathMatcher(String syntaxAndInput) {
        int pos = syntaxAndInput.indexOf(':');
        if (pos <= 0 || pos == syntaxAndInput.length()) {
            throw new IllegalArgumentException();
        }
        String syntax = syntaxAndInput.substring(0, pos);
        String input = syntaxAndInput.substring(pos + 1);
        String expr;
        if (syntax.equalsIgnoreCase(GLOB_SYNTAX)) {
            expr = FileSystemUtils.toRegexPattern(input);
        } else {
            if (syntax.equalsIgnoreCase(REGEX_SYNTAX)) {
                expr = input;
            } else {
                throw new UnsupportedOperationException("Syntax '" + syntax +
                        "' not recognized");
            }
        }
        // return matcher
        final Pattern pattern = Pattern.compile(expr);
        return path -> pattern.matcher(path.toString()).matches();
    }

    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException();
    }

    @Override
    public WatchService newWatchService() {
        throw new UnsupportedOperationException();
    }

    private void ensureOpen() {
        if (!isOpen) {
            throw new ClosedFileSystemException();
        }
    }

    private Optional<IndexNode> getInode(String entryName) {
        return Optional.ofNullable(entries.get(entryName));
    }

    private Optional<IndexNode> getInode(ZipPath path) {
        return getInode(path.toAbsolutePath().normalize().getEntryName());
    }

    private void buildTree() {
        entries.put("", root);

        for (ZipArchiveEntry entry : toIterable(zipFile.getEntriesInPhysicalOrder())) {
            List<String> components = getPathComponents(entry.getName());

            IndexNode node = new IndexNode(entry, entry.isDirectory(), String.join("/", components));
            entries.put(node.name, node);
            while (true) {
                if (components.size() == 0) break;
                if (components.size() == 1) {
                    node.sibling = root.child;
                    root.child = node;
                    break;
                }

                String parentName = String.join("/", components.subList(0, components.size() - 1));
                if (entries.containsKey(parentName)) {
                    IndexNode parent = entries.get(parentName);
                    node.sibling = parent.child;
                    parent.child = node;
                    break;
                }

                // Add new pseudo directory entry
                IndexNode parent = new IndexNode(null, true, parentName);
                entries.put(parentName, parent);
                node.sibling = parent.child;
                parent.child = node;
                node = parent;
            }
        }
    }

    private class IndexNode {
        private final ZipArchiveEntry entry;
        private final boolean isDirectory;
        private final String name;

        private ZipFileAttributes attributes;

        public IndexNode(ZipArchiveEntry entry, boolean isDirectory, String name) {
            this.entry = entry;
            this.isDirectory = isDirectory;
            this.name = name;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        public String getName() {
            return name;
        }

        public InputStream getInputStream() throws IOException {
            if (entry == null) throw new IOException("Entry " + name + " cannot open");
            return zipFile.getInputStream(entry);
        }

        public ZipFileAttributes getAttributes() {
            if (attributes == null) {
                if (entry == null) {
                    attributes = new ZipFileAttributes(0, false, false, true);
                } else {
                    attributes = new ZipFileAttributes(
                            entry.getSize(),
                            entry.isUnixSymlink(),
                            !entry.isDirectory(),
                            entry.isDirectory()
                    );
                }
            }
            return attributes;
        }

        IndexNode sibling;
        IndexNode child;
    }
}
