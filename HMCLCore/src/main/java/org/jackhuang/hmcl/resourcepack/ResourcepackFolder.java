package org.jackhuang.hmcl.resourcepack;

import java.nio.file.Files;
import java.nio.file.Path;

public class ResourcepackFolder implements ResourcepackFile {
    private final Path folder;

    public ResourcepackFolder(Path folder) {
        this.folder = folder;
    }

    @Override
    public String getName() {
        return folder.toFile().getName();
    }

    @Override
    public Path getPath() {
        return folder;
    }

    @Override
    public String getDescription() {
        try {
            return parseDescriptionFromJson(Files.readString(folder.resolve("pack.mcmeta")));
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return folder.resolve("pack.png");
    }
}
