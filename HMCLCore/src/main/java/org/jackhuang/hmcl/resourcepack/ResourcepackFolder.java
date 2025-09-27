package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackFolder implements ResourcepackFile {
    private final Path path;
    private final String description;

    public ResourcepackFolder(Path path) {
        this.path = path;
        String description = "";

        try {
            description = JsonUtils.fromJsonFile(path.resolve("pack.mcmeta"), ResourcepackMeta.class).pack().description();
        } catch (Exception e) {
            LOG.warning("Failed to parse resourcepack meta", e);
        }

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
    public String getDescription() {
        try {
            return description;
        } catch (Exception e) {
            LOG.warning("Failed to parse resourcepack meta", e);
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return path.resolve("pack.png");
    }
}
