package org.jackhuang.hmcl.resourcepack;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface ResourcepackFile {
    String getDescription();

    String getName();

    Path getPath();

    Path getIcon();

    default String parseDescriptionFromJson(String json) {
        try {
            return JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("pack").get("description").getAsString();
        } catch (Exception ignored) {
            return "";
        }

    }

    static ResourcepackFile parse(Path path) throws IOException {
        if (Files.isRegularFile(path) && path.toString().toLowerCase().endsWith(".zip")) {
            return new ResourcepackZipFile(path);
        } else if (Files.isDirectory(path) && Files.exists(path.resolve("pack.mcmeta"))) {
            return new ResourcepackFolder(path);
        }
        return null;
    }

}
