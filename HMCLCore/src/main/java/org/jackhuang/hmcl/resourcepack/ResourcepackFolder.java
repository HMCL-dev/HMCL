package org.jackhuang.hmcl.resourcepack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourcepackFolder implements ResourcepackFile {
    private final File folder;

    public ResourcepackFolder(File folder) {
        this.folder = folder;
    }

    @Override
    public String getName() {
        return folder.getName();
    }

    @Override
    public File getFile() {
        return folder;
    }

    @Override
    public String getDescription() {
        try {
            return parseDescriptionFromJson(Files.readString(folder.toPath().resolve("pack.mcmeta")));
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return folder.toPath().resolve("pack.png");
    }
}
