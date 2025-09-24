package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourcepackZipFile implements ResourcepackFile {
    private final FileSystem zipfs;
    private final Path resourcepackfile;

    public ResourcepackZipFile(Path resourcepackfile) throws IOException {
        this.resourcepackfile = resourcepackfile;
        this.zipfs = CompressingUtils.createReadOnlyZipFileSystem(resourcepackfile);
    }

    @Override
    public String getName() {
        return resourcepackfile.toFile().getName().replace(".zip", "");
    }

    @Override
    public Path getPath() {
        return resourcepackfile;
    }

    @Override
    public String getDescription() {
        try {
            return parseDescriptionFromJson(Files.readString(zipfs.getPath("pack.mcmeta")));
        } catch (Exception ignored) {
            return "";
        }
    }

    @Override
    public Path getIcon() {
        return zipfs.getPath("pack.png");
    }
}

