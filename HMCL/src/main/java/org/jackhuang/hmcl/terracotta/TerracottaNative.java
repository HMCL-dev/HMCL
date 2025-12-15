/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.terracotta;

import kala.compress.archivers.tar.TarArchiveEntry;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.tree.TarFileTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CancellationException;

public final class TerracottaNative {
    private final List<URI> links;
    private final FileDownloadTask.IntegrityCheck checking;
    private final Path path;

    public TerracottaNative(List<URI> links, Path path, FileDownloadTask.IntegrityCheck checking) {
        this.links = links;
        this.path = path;
        this.checking = checking;
    }

    public Path getPath() {
        return path;
    }

    public Task<?> install(ITerracottaProvider.Context context, @Nullable TarFileTree tree) {
        if (tree == null) {
            return new FileDownloadTask(links, path, checking) {
                @Override
                protected Context getContext(HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
                    Context delegate = super.getContext(response, checkETag, bmclapiHash);
                    return new Context() {
                        @Override
                        public void withResult(boolean success) {
                            delegate.withResult(success);
                        }

                        @Override
                        public void write(byte[] buffer, int offset, int len) throws IOException {
                            if (!context.hasInstallFence()) {
                                throw new CancellationException("User has installed terracotta from local archives.");
                            }
                            delegate.write(buffer, offset, len);
                        }

                        @Override
                        public void close() throws IOException {
                            if (isSuccess() && !context.requestInstallFence()) {
                                throw new CancellationException();
                            }

                            delegate.close();
                        }
                    };
                }
            };
        }

        return Task.runAsync(() -> {
            String name = FileUtils.getName(path);
            TarArchiveEntry entry = tree.getRoot().getFiles().get(name);
            if (entry == null) {
                throw new ITerracottaProvider.ArchiveFileMissingException("Cannot exact entry: " + name);
            }

            if (!context.requestInstallFence()) {
                throw new CancellationException();
            }

            Files.createDirectories(path.toAbsolutePath().getParent());

            MessageDigest digest = DigestUtils.getDigest(checking.getAlgorithm());
            try (
                    InputStream stream = tree.getInputStream(entry);
                    OutputStream os = Files.newOutputStream(path)
            ) {
                stream.transferTo(new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        os.write(b);
                        digest.update((byte) b);
                    }

                    @Override
                    public void write(byte @NotNull [] buffer, int offset, int len) throws IOException {
                        os.write(buffer, offset, len);
                        digest.update(buffer, offset, len);
                    }
                });
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            if (!checksum.equalsIgnoreCase(checking.getChecksum())) {
                Files.delete(path);
                throw new ITerracottaProvider.ArchiveFileMissingException("Incorrect checksum (" + checking.getAlgorithm() + "), expected: " + checking.getChecksum() + ", actual: " + checksum);
            }
        });
    }

    public ITerracottaProvider.Status status() throws IOException {
        if (Files.exists(path)) {
            if (DigestUtils.digestToString(checking.getAlgorithm(), path).equalsIgnoreCase(checking.getChecksum())) {
                return ITerracottaProvider.Status.READY;
            }
        }

        try {
            if (TerracottaMetadata.hasLegacyVersionFiles()) {
                return ITerracottaProvider.Status.LEGACY_VERSION;
            }
        } catch (IOException e) {
            Logger.LOG.warning("Cannot determine whether legacy versions exist.");
        }
        return ITerracottaProvider.Status.NOT_EXIST;
    }
}
