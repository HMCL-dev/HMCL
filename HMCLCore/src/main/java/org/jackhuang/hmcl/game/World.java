package org.jackhuang.hmcl.game;

import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Unzipper;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class World {

    private final Path file;
    private final String name;

    public World(Path file) throws IOException {
        this.file = file;

        if (Files.isDirectory(file))
            name = loadFromDirectory();
        else if (Files.isRegularFile(file))
            name = loadFromZip();
        else
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
    }

    private String loadFromDirectory() {
        return FileUtils.getName(file);
    }

    private String loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
            Path root = Files.list(fs.getPath("/")).filter(Files::isDirectory).findAny()
                    .orElseThrow(() -> new IOException("Not a valid world zip file"));

            Path levelDat = root.resolve("level.dat");
            if (!Files.exists(levelDat))
                throw new FileNotFoundException("Not a valid world zip file since level.dat cannot be found.");

            return FileUtils.getName(root);
        }
    }

    public void install(Path savesDir) throws IOException {
        Path worldDir = savesDir.resolve(name);
        if (Files.isDirectory(worldDir)) {
            throw new FileAlreadyExistsException("World already exists");
        }

        if (Files.isRegularFile(file)) {
            new Unzipper(file, savesDir)
                    .setSubDirectory("/" + name + "/")
                    .unzip();
        } else if (Files.isDirectory(file)) {
            FileUtils.copyDirectory(file, worldDir);
        }
    }
}
