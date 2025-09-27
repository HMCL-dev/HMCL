package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackZipFile implements ResourcepackFile {
    private final FileSystem zipfs;
    private final Path resourcepackfile;
    private final String name;
    private final String description;

    public ResourcepackZipFile(Path resourcepackfile) throws IOException {
        this.resourcepackfile = resourcepackfile;
        this.zipfs = CompressingUtils.createReadOnlyZipFileSystem(resourcepackfile);
        String fileName = resourcepackfile.getFileName().toString();
        name = fileName.substring(0, fileName.length() - 4);

        String description = "";
        try {
            description = JsonUtils.fromJsonFile(resourcepackfile.resolve("pack.mcmeta"), ResourcepackMeta.class).pack().description();
        } catch (Exception e) {
            LOG.warning("Failed to parse resourcepack meta", e);
        }
        this.description = description;
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
        return description;
    }

    @Override
    public Path getIcon() {
        return zipfs.getPath("pack.png");
    }
}

