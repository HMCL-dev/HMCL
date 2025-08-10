package org.jackhuang.hmcl.terracotta;

import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class TerracottaDaemon {
    private final List<URI> links;
    private final FileDownloadTask.IntegrityCheck checking;
    private final Path path;

    public TerracottaDaemon(List<URI> links, String classifier, FileDownloadTask.IntegrityCheck checking) {
        this.links = links;
        this.checking = checking;
        this.path = Metadata.DEPENDENCIES_DIRECTORY.resolve(
                String.format("terracota/%s/terracotta-%s", TerracottaMetadata.VERSION, classifier)
        ).toAbsolutePath();
    }

    public Path getPath() {
        return path;
    }

    public FileDownloadTask create() {
        return new FileDownloadTask(links, path, checking);
    }

    public boolean exists() throws IOException {
        if (!Files.exists(path)) {
            return false;
        }

        String checksum;
        try (InputStream is = Files.newInputStream(path)) {
            checksum = Hex.encodeHex(DigestUtils.digest(checking.getAlgorithm(), is));
        }
        return checksum.equalsIgnoreCase(checking.getChecksum());
    }
}
