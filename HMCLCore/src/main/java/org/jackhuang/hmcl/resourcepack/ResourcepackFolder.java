package org.jackhuang.hmcl.resourcepack;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackFolder implements ResourcepackFile {
    private final Path folder;

    public ResourcepackFolder(Path folder) {
        this.folder = folder;
    }

    @Override
    public String getName() {
        return folder.getFileName().toString();
    }

    @Override
    public Path getPath() {
        return folder;
    }

    @Override
    public String getDescription() {
        try {
            return parseDescriptionFromJson(folder.resolve("pack.mcmeta"));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return folder.resolve("pack.png");
    }
}
