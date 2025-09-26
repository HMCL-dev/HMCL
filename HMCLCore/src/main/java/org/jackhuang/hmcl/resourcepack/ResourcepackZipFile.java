package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

public final class ResourcepackZipFile implements ResourcepackFile {
    private final FileSystem zipfs;
    private final Path resourcepackfile;
    private final String name;

    public ResourcepackZipFile(Path resourcepackfile) throws IOException {
        this.resourcepackfile = resourcepackfile;
        this.zipfs = CompressingUtils.createReadOnlyZipFileSystem(resourcepackfile);
        String fileName = resourcepackfile.getFileName().toString();
        name = fileName.substring(0, fileName.length() - 4);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Path getPath() {
        return resourcepackfile;
    }

    @Override
    public String getDescription() {
        try {
            return parseDescriptionFromJson(zipfs.getPath("pack.mcmeta"));
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return zipfs.getPath("pack.png");
    }
}

