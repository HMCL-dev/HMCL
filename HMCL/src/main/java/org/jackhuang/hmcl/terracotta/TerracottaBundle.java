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
import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.provider.AbstractTerracottaProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.logging.Logger;
import org.jackhuang.hmcl.util.tree.TarFileTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

public final class TerracottaBundle {
    private final Path root;

    private final List<URI> links;

    private final FileDownloadTask.IntegrityCheck hash;

    private final Map<String, FileDownloadTask.IntegrityCheck> files;

    private final TerracottaMetadata.Options options;

    public TerracottaBundle(Path root, List<URI> links, FileDownloadTask.IntegrityCheck hash, Map<String, FileDownloadTask.IntegrityCheck> files, TerracottaMetadata.Options options) {
        this.root = root;
        this.links = links;
        this.hash = hash;
        this.files = files;
        this.options = options;
    }

    public Task<Path> download(AbstractTerracottaProvider.DownloadContext context) {
        return Task.supplyAsync(() -> Files.createTempFile("terracotta-", ".tar.gz"))
                .thenComposeAsync(Schedulers.javafx(), pkg -> {
                    FileDownloadTask download = new FileDownloadTask(links, pkg, hash) {
                        @Override
                        protected Context getContext(HttpResponse<?> response, boolean checkETag, String bmclapiHash) throws IOException {
                            FetchTask.Context delegate = super.getContext(response, checkETag, bmclapiHash);
                            return new Context() {
                                @Override
                                public void withResult(boolean success) {
                                    delegate.withResult(success);
                                }

                                @Override
                                public void write(byte[] buffer, int offset, int len) throws IOException {
                                    context.checkCancellation();
                                    delegate.write(buffer, offset, len);
                                }

                                @Override
                                public void close() throws IOException {
                                    if (isSuccess()) {
                                        context.checkCancellation();
                                    }

                                    delegate.close();
                                }
                            };
                        }
                    };

                    context.bindProgress(download.progressProperty());
                    return download.thenSupplyAsync(() -> pkg);
                });
    }

    public Task<?> install(Path pkg) {
        return Task.runAsync(() -> {
            Files.createDirectories(root);
            FileUtils.cleanDirectory(root);

            try (TarFileTree tree = TarFileTree.open(pkg)) {
                for (Map.Entry<String, FileDownloadTask.IntegrityCheck> entry : files.entrySet()) {
                    String file = entry.getKey();
                    FileDownloadTask.IntegrityCheck check = entry.getValue();

                    Path path = root.resolve(file);
                    TarArchiveEntry archive = tree.getEntry("/" + file);
                    if (archive == null) {
                        throw new ArtifactMalformedException(String.format("Expecting %s file in terracotta bundle.", file));
                    }

                    MessageDigest digest = DigestUtils.getDigest(check.getAlgorithm());
                    try (
                            InputStream is = tree.getInputStream(archive);
                            OutputStream os = new DigestOutputStream(Files.newOutputStream(path), digest)
                    ) {
                        is.transferTo(os);
                    }

                    String hash = HexFormat.of().formatHex(digest.digest());
                    if (!check.getChecksum().equalsIgnoreCase(hash)) {
                        throw new ChecksumMismatchException(check.getAlgorithm(), check.getChecksum(), hash);
                    }

                    EnumSet<PosixFilePermission> permission = FileUtils.parsePosixFilePermission(archive.getMode());
                    try {
                        Files.setPosixFilePermissions(path, permission);
                    } catch (UnsupportedOperationException ignored) {
                    }
                }
            }
        }).whenComplete(exception -> {
            if (exception != null) {
                FileUtils.deleteDirectory(root);
            }
        });
    }

    public Path locate(String file) {
        file = options.replace(file);

        FileDownloadTask.IntegrityCheck check = files.get(file);
        if (check == null) {
            throw new AssertionError(String.format("Expecting %s file in terracotta bundle.", file));
        }
        return root.resolve(file).toAbsolutePath();
    }

    public AbstractTerracottaProvider.Status status() throws IOException {
        if (Files.exists(root)) {
            boolean matched = true;
            for (Map.Entry<String, FileDownloadTask.IntegrityCheck> entry : files.entrySet()) {
                String file = entry.getKey();
                FileDownloadTask.IntegrityCheck check = entry.getValue();

                Path path = root.resolve(file);
                if (!Files.isReadable(path) || !DigestUtils.digestToString(check.getAlgorithm(), path).equalsIgnoreCase(check.getChecksum())) {
                    matched = false;
                    break;
                }
            }

            if (matched) {
                return AbstractTerracottaProvider.Status.READY;
            }
        }

        try {
            if (TerracottaMetadata.hasLegacyVersionFiles()) {
                return AbstractTerracottaProvider.Status.LEGACY_VERSION;
            }
        } catch (IOException e) {
            Logger.LOG.warning("Cannot determine whether legacy versions exist.");
        }
        return AbstractTerracottaProvider.Status.NOT_EXIST;
    }
}
