package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackZipFile implements ResourcepackFile {
    private final Path path;
    private final byte[] icon;
    private final String name;
    private final LocalModFile.Description description;

    public ResourcepackZipFile(Path path) throws IOException {
        this.path = path;
        LocalModFile.Description description = null;

        byte[] icon = new byte[0];

        try (var zipFileTree = ZipFileTree.open(path)) {
            try {
                description = JsonUtils.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"), PackMcMeta.class).getPackInfo().getDescription();
            } catch (Exception e) {
                LOG.warning("Failed to parse resourcepack meta", e);
            }

            try (InputStream is = zipFileTree.getInputStream("/pack.png")) {
                icon = is.readAllBytes();
            } catch (Exception e) {
                LOG.warning("Failed to load resourcepack icon", e);
            }
        }

        this.icon = icon;
        this.description = description;

        String fileName = path.getFileName().toString();
        name = fileName.substring(0, fileName.length() - 4);
    }

    @Override
    public String getName() {
        return name;
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

