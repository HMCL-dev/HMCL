package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackFolder extends ResourcePackFile {
    private final PackMcMeta meta;
    private final byte @Nullable [] icon;

    public ResourcePackFolder(ResourcePackManager manager, Path path) {
        super(manager, path);

        PackMcMeta meta = null;
        try {
            meta = JsonUtils.fromJsonFile(path.resolve("pack.mcmeta"), PackMcMeta.class);
        } catch (Exception e) {
            LOG.warning("Failed to parse resource pack meta", e);
        }
        this.meta = meta;

        byte[] icon;
        try {
            icon = Files.readAllBytes(path.resolve("pack.png"));
        } catch (IOException e) {
            icon = null;
            LOG.warning("Failed to read resource pack icon", e);
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
        FileUtils.deleteDirectory(file);
    }

    @Override
    public ModUpdate checkUpdates(String gameVersion, RemoteModRepository repository) {
        return null;
    }
}
