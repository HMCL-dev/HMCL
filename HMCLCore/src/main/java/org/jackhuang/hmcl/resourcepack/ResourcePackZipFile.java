package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {
    private final PackMcMeta meta;
    private final byte @Nullable [] icon;

    public ResourcePackZipFile(ResourcePackManager manager, Path path) throws IOException {
        super(manager, path);

        PackMcMeta meta = null;
        byte[] icon = null;

        try (var zipFileTree = new ZipFileTree(CompressingUtils.openZipFile(path))) {
            try {
                meta = JsonUtils.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"), PackMcMeta.class);
            } catch (Exception e) {
                LOG.warning("Failed to parse resource pack meta", e);
            }
            this.meta = meta;

            var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry != null) {
                try (InputStream is = zipFileTree.getInputStream(iconEntry)) {
                    icon = is.readAllBytes();
                } catch (Exception e) {
                    LOG.warning("Failed to load resource pack icon", e);
                }
            }
        }

        this.icon = icon;
    }

    @Override
    public PackMcMeta getMeta() {
        return meta;
    }

    @Override
    public byte @Nullable [] getIcon() {
        return icon;
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(path);
    }
}

