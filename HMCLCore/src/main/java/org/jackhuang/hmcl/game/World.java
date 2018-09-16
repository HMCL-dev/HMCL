package org.jackhuang.hmcl.game;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.LongTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Unzipper;
import org.jackhuang.hmcl.util.Zipper;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class World {

    private final Path file;
    private String fileName;
    private String worldName;
    private String gameVersion;
    private long lastPlayed, seed;

    public World(Path file) throws IOException {
        this.file = file;

        if (Files.isDirectory(file))
            loadFromDirectory();
        else if (Files.isRegularFile(file))
            loadFromZip();
        else
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
    }

    private void loadFromDirectory() throws IOException {
        fileName = FileUtils.getName(file);
        Path levelDat = file.resolve("level.dat");
        getWorldName(levelDat);
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getLastPlayed() {
        return lastPlayed;
    }

    public long getSeed() {
        return seed;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    private void loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
            Path root = Files.list(fs.getPath("/")).filter(Files::isDirectory).findAny()
                    .orElseThrow(() -> new IOException("Not a valid world zip file"));

            Path levelDat = root.resolve("level.dat");
            if (!Files.exists(levelDat))
                throw new IllegalArgumentException("Not a valid world zip file since level.dat cannot be found.");

            fileName = FileUtils.getName(root);
            getWorldName(levelDat);
        }
    }

    private void getWorldName(Path levelDat) throws IOException {
        CompoundTag nbt = parseLevelDat(levelDat);

        CompoundTag data = nbt.get("Data");
        String name = data.<StringTag>get("LevelName").getValue();
        lastPlayed = data.<LongTag>get("LastPlayed").getValue();
        seed = data.<LongTag>get("RandomSeed").getValue();
        CompoundTag version = data.get("Version");
        gameVersion = version.<StringTag>get("Name").getValue();
        worldName = name;
    }

    public void rename(String newName) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException("Not a valid world directory");

        // Change the name recorded in level.dat
        Path levelDat = file.resolve("level.dat");
        CompoundTag nbt = parseLevelDat(levelDat);
        CompoundTag data = nbt.get("Data");
        data.put(new StringTag("LevelName", newName));

        NBTIO.writeTag(new GZIPOutputStream(Files.newOutputStream(levelDat)), nbt);

        // then change the folder's name
        Files.move(file, file.resolveSibling(newName));
    }

    public void install(Path savesDir, String name) throws IOException {
        Path worldDir = savesDir.resolve(name);
        if (Files.isDirectory(worldDir)) {
            throw new FileAlreadyExistsException("World already exists");
        }

        if (Files.isRegularFile(file)) {
            String subDirectoryName;
            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
                List<Path> subDirs = Files.list(fs.getPath("/")).collect(Collectors.toList());
                if (subDirs.size() != 1) {
                    throw new IOException("World zip malformed");
                }
                subDirectoryName = FileUtils.getName(subDirs.get(0));
            }
            new Unzipper(file, savesDir)
                    .setSubDirectory("/" + subDirectoryName + "/")
                    .unzip();
        } else if (Files.isDirectory(file)) {
            FileUtils.copyDirectory(file, worldDir);
        }
    }

    public void export(Path zip, String worldName) throws IOException {
        if (!Files.isDirectory(file))
            throw new IOException();

        try (Zipper zipper = new Zipper(zip)) {
            zipper.putDirectory(file, "/" + worldName + "/");
        }
    }

    private static CompoundTag parseLevelDat(Path path) throws IOException {
        Tag nbt = NBTIO.readTag(new GZIPInputStream(Files.newInputStream(path)));
        if (nbt instanceof CompoundTag)
            return (CompoundTag) nbt;
        else
            throw new IOException("level.dat malformed");
    }

    public static List<World> getWorlds(Path worldDir) throws IOException {
        List<World> worlds = new ArrayList<>();
        for (Path world : Files.newDirectoryStream(worldDir)) {
            worlds.add(new World(world));
        }
        return worlds;
    }
}
