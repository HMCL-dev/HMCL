package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public interface ResourcepackFile {
    String getDescription();

    String getName();

    Path getPath();

    Path getIcon();

    default String parseDescriptionFromJson(Path json) {
        try {
            return JsonUtils.fromJsonFile(json, ResourcepackMeta.class).pack.description;
        } catch (Exception e) {
            LOG.warning("Failed to parse resourcepack meta ", e);
            return "";
        }

    }

    static ResourcepackFile parse(Path path) throws IOException {
        String fileName = path.getFileName().toString();
        if (Files.isRegularFile(path) && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return new ResourcepackZipFile(path);
        } else if (Files.isDirectory(path) && Files.exists(path.resolve("pack.mcmeta"))) {
            return new ResourcepackFolder(path);
        }
        return null;
    }

}
