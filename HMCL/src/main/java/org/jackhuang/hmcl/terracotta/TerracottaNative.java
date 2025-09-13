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

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    public Task<?> create() {
        return new FileDownloadTask(links, path, checking);
    }

    public ITerracottaProvider.Status status() throws IOException {
        if (Files.exists(path)) {
            String checksum;
            try (InputStream is = Files.newInputStream(path)) {
                checksum = DigestUtils.digestToString(checking.getAlgorithm(), is);
            }
            if (checksum.equalsIgnoreCase(checking.getChecksum())) {
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
