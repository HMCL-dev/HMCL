package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.io.CompressingUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class ResourcepackZipFile implements ResourcepackFile {
    private final FileSystem zipfs;
    private final File resourcepackfile;

    public ResourcepackZipFile(File resourcepackfile) throws IOException {
        this.resourcepackfile = resourcepackfile;
        this.zipfs = CompressingUtils.createReadOnlyZipFileSystem(resourcepackfile.toPath());
    }

    @Override
    public String getName() {
        return resourcepackfile.getName().replace(".zip", "");
    }

    @Override
    public File getFile() {
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

