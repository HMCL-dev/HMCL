package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackZipFile implements ResourcepackFile {
    private final Path path;
    private final Path iconPath;
    private final String name;
    private final LocalModFile.Description description;

    public ResourcepackZipFile(Path path) throws IOException {
        this.path = path;

        LocalModFile.Description description = null;
        Path iconPath = null;

        try (var zipFileTree = ZipFileTree.open(path)) {
            try (InputStream is = zipFileTree.getInputStream("pack.mcmeta")){
                description = JsonUtils.fromJsonFully(is, PackMcMeta.class).getPackInfo().getDescription();
            } catch (Exception e) {
                LOG.warning("Failed to parse resourcepack meta", e);
            }

            try (InputStream is = zipFileTree.getInputStream("pack.png")) {
                Path tempFile = Files.createTempFile("hmcl-pack-icon-", ".png");
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
                iconPath = tempFile;
            } catch (Exception e) {
                LOG.warning("Failed to load resourcepack icon", e);
            }
        }

        this.description = description;
        this.iconPath = iconPath;

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
    public Path getIcon() {
        return iconPath;
    }
}

