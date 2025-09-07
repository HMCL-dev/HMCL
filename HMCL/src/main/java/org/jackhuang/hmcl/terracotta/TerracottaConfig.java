package org.jackhuang.hmcl.terracotta;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.terracotta.provider.ITerracottaProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TerracottaConfig {
    private final List<URI> links;
    private final FileDownloadTask.IntegrityCheck checking;
    private final Path path;
    private final List<Path> legacy;

    public TerracottaConfig(List<URI> links, Path path, List<Path> legacy, FileDownloadTask.IntegrityCheck checking) {
        this.links = links;
        this.path = path;
        this.checking = checking;
        this.legacy = legacy;
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
                checksum = Hex.encodeHex(DigestUtils.digest(checking.getAlgorithm(), is));
            }
            if (checksum.equalsIgnoreCase(checking.getChecksum())) {
                return ITerracottaProvider.Status.READY;
            }
        }

        for (Path legacy : legacy) {
            if (Files.exists(legacy)) {
                return ITerracottaProvider.Status.LEGACY_VERSION;
            }
        }
        return ITerracottaProvider.Status.NOT_EXIST;
    }
}
