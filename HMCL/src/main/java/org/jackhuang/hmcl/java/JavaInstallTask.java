/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.java;

import kala.compress.archivers.ArchiveEntry;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.tree.ArchiveFileTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Glavo
 */
public final class JavaInstallTask extends Task<JavaManifest> {

    private final Path targetDir;
    private final Map<String, Object> update;
    private final Path archiveFile;

    private final Map<String, JavaLocalFiles.Local> files = new LinkedHashMap<>();
    private final ArrayList<String> nameStack = new ArrayList<>();
    private final byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
    private final MessageDigest messageDigest = DigestUtils.getDigest("SHA-1");

    public JavaInstallTask(Path targetDir, Map<String, Object> update, Path archiveFile) {
        this.targetDir = targetDir;
        this.update = update;
        this.archiveFile = archiveFile;
    }

    @Override
    public void execute() throws Exception {
        JavaInfo info;

        try (ArchiveFileTree<?, ?> tree = ArchiveFileTree.open(archiveFile)) {
            info = JavaInfo.fromArchive(tree);
            copyDirContent(tree, targetDir);
        }

        setResult(new JavaManifest(info, update, files));
    }

    private <F, E extends ArchiveEntry> void copyDirContent(ArchiveFileTree<F, E> tree, Path targetDir) throws IOException {
        copyDirContent(tree, tree.getRoot().getSubDirs().values().iterator().next(), targetDir);
    }

    private <F, E extends ArchiveEntry> void copyDirContent(ArchiveFileTree<F, E> tree, ArchiveFileTree.Dir<E> dir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        for (Map.Entry<String, E> pair : dir.getFiles().entrySet()) {
            Path path = targetDir.resolve(pair.getKey());
            E entry = pair.getValue();

            nameStack.add(pair.getKey());
            if (tree.isLink(entry)) {
                String linkTarget = tree.getLink(entry);
                files.put(String.join("/", nameStack), new JavaLocalFiles.LocalLink(linkTarget));
                Files.createSymbolicLink(path, Paths.get(linkTarget));
            } else {
                long size = 0L;

                try (InputStream input = tree.getInputStream(entry);
                     OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    messageDigest.reset();

                    int c;
                    while ((c = input.read(buffer)) > 0) {
                        size += c;
                        output.write(buffer, 0, c);
                        messageDigest.update(buffer, 0, c);
                    }
                }

                if (tree.isExecutable(entry))
                    FileUtils.setExecutable(path);

                files.put(String.join("/", nameStack), new JavaLocalFiles.LocalFile(HexFormat.of().formatHex(messageDigest.digest()), size));
            }
            nameStack.remove(nameStack.size() - 1);
        }

        for (Map.Entry<String, ArchiveFileTree.Dir<E>> pair : dir.getSubDirs().entrySet()) {
            nameStack.add(pair.getKey());
            files.put(String.join("/", nameStack), new JavaLocalFiles.LocalDirectory());
            copyDirContent(tree, pair.getValue(), targetDir.resolve(pair.getKey()));
            nameStack.remove(nameStack.size() - 1);
        }
    }
}
