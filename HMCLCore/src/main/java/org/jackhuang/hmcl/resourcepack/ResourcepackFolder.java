package org.jackhuang.hmcl.resourcepack;

import java.nio.file.Path;

public record ResourcepackFolder(Path path) implements ResourcepackFile {

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
            return parseDescriptionFromJson(path.resolve("pack.mcmeta"));
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return path.resolve("pack.png");
    }
}
