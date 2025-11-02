package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackFolder implements ResourcepackFile {
    private final Path path;
    private final LocalModFile.Description description;
    private final byte[] icon;

    public ResourcepackFolder(Path path) {
        this.path = path;

        LocalModFile.Description description = null;
        try {
            description = JsonUtils.fromJsonFile(path.resolve("pack.mcmeta"), PackMcMeta.class).getPackInfo().getDescription();
        } catch (Exception e) {
            LOG.warning("Failed to parse resourcepack meta", e);
        }

        byte[] icon = new byte[0];
        try {
            icon = Files.readAllBytes(path.resolve("pack.png"));
        } catch (IOException e) {
            LOG.warning("Failed to read resourcepack icon", e);
        }
        this.icon = icon;

        this.description = description;
    }

    @Override
    public String getName() {
        return path.getFileName().toString();
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public LocalModFile.Description getDescription() {
        return description;
    }

    @Override
    public byte[] getIcon() {
        return icon;
    }
}
