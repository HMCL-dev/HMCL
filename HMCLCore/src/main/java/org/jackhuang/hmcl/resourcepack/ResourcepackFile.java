package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public interface ResourcepackFile {
    @Nullable
    LocalModFile.Description getDescription();

    String getName();

    Path getPath();

    byte @Nullable [] getIcon();

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
