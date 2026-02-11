/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.game;

import com.github.steveice10.opennbt.NBTIO;
import com.github.steveice10.opennbt.tag.builtin.*;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.io.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class World {

    private final Path file;
    private String fileName;
    private CompoundTag levelData;
    private Image icon;
    private Path levelDataPath;

    public World(Path file) throws IOException {
        this.file = file;

        if (Files.isDirectory(file)) {
            loadFromDirectory();
        } else if (Files.isRegularFile(file)) {
            loadFromZip();
        } else {
            throw new IOException("Path " + file + " cannot be recognized as a Minecraft world");
        }
    }

    public Path getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }

    public String getWorldName() {
        CompoundTag data = levelData.get("Data");
        StringTag levelNameTag = data.get("LevelName");
        return levelNameTag.getValue();
    }

    public void setWorldName(String worldName) throws IOException {
        if (levelData.get("Data") instanceof CompoundTag data && data.get("LevelName") instanceof StringTag levelNameTag) {
            levelNameTag.setValue(worldName);
            writeLevelDat(levelData);
        }
    }

    public Path getLevelDatFile() {
        return file.resolve("level.dat");
    }

    public Path getSessionLockFile() {
        return file.resolve("session.lock");
    }

    public CompoundTag getLevelData() {
        return levelData;
    }

    public long getLastPlayed() {
        CompoundTag data = levelData.get("Data");
        LongTag lastPlayedTag = data.get("LastPlayed");
        return lastPlayedTag.getValue();
    }

    public @Nullable GameVersionNumber getGameVersion() {
        if (levelData.get("Data") instanceof CompoundTag data
                && data.get("Version") instanceof CompoundTag versionTag
                && versionTag.get("Name") instanceof StringTag nameTag) {
            return GameVersionNumber.asGameVersion(nameTag.getValue());
        }
        return null;
    }

    public @Nullable Long getSeed() {
        CompoundTag data = levelData.get("Data");
        if (data.get("WorldGenSettings") instanceof CompoundTag worldGenSettingsTag && worldGenSettingsTag.get("seed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        } else if (data.get("RandomSeed") instanceof LongTag seedTag) {
            return seedTag.getValue();
        }
        return null;
    }

    public boolean isLargeBiomes() {
        CompoundTag data = levelData.get("Data");
        if (data.get("generatorName") instanceof StringTag generatorNameTag) {
            return "largeBiomes".equals(generatorNameTag.getValue());
        } else {
            if (data.get("WorldGenSettings") instanceof CompoundTag worldGenSettingsTag
                    && worldGenSettingsTag.get("dimensions") instanceof CompoundTag dimensionsTag
                    && dimensionsTag.get("minecraft:overworld") instanceof CompoundTag overworldTag
                    && overworldTag.get("generator") instanceof CompoundTag generatorTag) {
                if (generatorTag.get("biome_source") instanceof CompoundTag biomeSourceTag
                        && biomeSourceTag.get("large_biomes") instanceof ByteTag largeBiomesTag) {
                    return largeBiomesTag.getValue() == (byte) 1;
                } else if (generatorTag.get("settings") instanceof StringTag settingsTag) {
                    return "minecraft:large_biomes".equals(settingsTag.getValue());
                }
            }
            return false;
        }
    }

    public Image getIcon() {
        return icon;
    }

    public boolean isLocked() {
        return isLocked(getSessionLockFile());
    }

    public boolean supportDatapacks() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.13", "17w43a");
    }

    public boolean supportQuickPlay() {
        return getGameVersion() != null && getGameVersion().isAtLeast("1.20", "23w14a");
    }

    public static boolean supportQuickPlay(GameVersionNumber gameVersionNumber) {
        return gameVersionNumber != null && gameVersionNumber.isAtLeast("1.20", "23w14a");
    }

    private void loadFromDirectory() throws IOException {
        fileName = FileUtils.getName(file);
        Path levelDat = file.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            levelDat = file.resolve("special_level.dat");
        }
        if (!Files.exists(levelDat)) {
            throw new IOException("Not a valid world directory since level.dat or special_level.dat cannot be found.");
        }
        loadAndCheckLevelDat(levelDat);
        this.levelDataPath = levelDat;

        Path iconFile = file.resolve("icon.png");
        if (Files.isRegularFile(iconFile)) {
            try (InputStream inputStream = Files.newInputStream(iconFile)) {
                icon = new Image(inputStream, 64, 64, true, false);
                if (icon.isError()) {
                    throw icon.getException();
                }
            } catch (Exception e) {
                LOG.warning("Failed to load world icon", e);
            }
        }
    }

    private void loadFromZipImpl(Path root) throws IOException {
        Path levelDat = root.resolve("level.dat");
        if (!Files.exists(levelDat)) {
            levelDat = root.resolve("special_level.dat");
        }
        if (!Files.exists(levelDat)) {
            throw new IOException("Not a valid world zip file since level.dat or special_level.dat cannot be found.");
        }
        loadAndCheckLevelDat(levelDat);

        Path iconFile = root.resolve("icon.png");
        if (Files.isRegularFile(iconFile)) {
            try (InputStream inputStream = Files.newInputStream(iconFile)) {
                icon = new Image(inputStream, 64, 64, true, false);
                if (icon.isError()) {
                    throw icon.getException();
                }
            } catch (Exception e) {
                LOG.warning("Failed to load world icon", e);
            }
        }
    }

    private void loadFromZip() throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(file).setAutoDetectEncoding(true).build()) {
            Path levelDatPath = fs.getPath("/level.dat");
            if (Files.isRegularFile(levelDatPath)) {
                fileName = FileUtils.getName(file);
                loadFromZipImpl(fs.getPath("/"));
                return;
            }
            try (Stream<Path> stream = Files.list(fs.getPath("/"))) {
                Path root = stream.filter(Files::isDirectory).findAny()
                        .orElseThrow(() -> new IOException("Not a valid world zip file"));
                fileName = FileUtils.getName(root);
                loadFromZipImpl(root);
            }
        }
    }

    private void loadAndCheckLevelDat(Path levelDat) throws IOException {
        this.levelData = parseLevelDat(levelDat);
        CompoundTag data = levelData.get("Data");
        if (data == null) {
            throw new IOException("level.dat missing Data");
        }
        if (!(data.get("LevelName") instanceof StringTag)) {
            throw new IOException("level.dat missing LevelName");
        }
        if (!(data.get("LastPlayed") instanceof LongTag)) {
            throw new IOException("level.dat missing LastPlayed");
        }
    }

    public void reloadLevelDat() throws IOException {
        if (levelDataPath != null) {
            loadAndCheckLevelDat(this.levelDataPath);
        }
    }

    // The rename method is used to rename temporary world object during installation and copying,
    // so there is no need to modify the `file` field.
    public void rename(String newName) throws IOException {
        if (!Files.isDirectory(file)) {
            throw new IOException("Not a valid world directory");
        }

        // Change the name recorded in level.dat
        CompoundTag data = levelData.get("Data");
        data.put(new StringTag("LevelName", newName));
        writeLevelDat(levelData);

        // then change the folder's name
        Files.move(file, file.resolveSibling(newName));
    }

    public void install(Path savesDir, String name) throws IOException {
        Path worldDir;
        try {
            worldDir = savesDir.resolve(name);
        } catch (InvalidPathException e) {
            throw new IOException(e);
        }

        if (Files.isDirectory(worldDir)) {
            throw new FileAlreadyExistsException("World already exists");
        }

        if (Files.isRegularFile(file)) {
            try (FileSystem fs = CompressingUtils.readonly(file).setAutoDetectEncoding(true).build()) {
                Path levelDatPath = fs.getPath("/level.dat");
                if (Files.isRegularFile(levelDatPath)) {
                    fileName = FileUtils.getName(file);

                    new Unzipper(file, worldDir).unzip();
                } else {
                    try (Stream<Path> stream = Files.list(fs.getPath("/"))) {
                        List<Path> subDirs = stream.toList();
                        if (subDirs.size() != 1) {
                            throw new IOException("World zip malformed");
                        }
                        String subDirectoryName = FileUtils.getName(subDirs.get(0));
                        new Unzipper(file, worldDir)
                                .setSubDirectory("/" + subDirectoryName + "/")
                                .unzip();
                    }
                }

            }
            new World(worldDir).rename(name);
        } else if (Files.isDirectory(file)) {
            FileUtils.copyDirectory(file, worldDir);
        }
    }

    public void export(Path zip, String worldName) throws IOException {
        if (!Files.isDirectory(file)) {
            throw new IOException();
        }

        try (Zipper zipper = new Zipper(zip)) {
            zipper.putDirectory(file, worldName);
        }
    }

    public void delete() throws IOException {
        if (isLocked()) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }
        FileUtils.forceDelete(file);
    }

    public void copy(String newName) throws IOException {
        if (!Files.isDirectory(file)) {
            throw new IOException("Not a valid world directory");
        }

        if (isLocked()) {
            throw new WorldLockedException("The world " + getFile() + " has been locked");
        }

        Path newPath = file.resolveSibling(newName);
        FileUtils.copyDirectory(file, newPath, path -> !path.contains("session.lock"));
        World newWorld = new World(newPath);
        newWorld.rename(newName);
    }

    public FileChannel lock() throws WorldLockedException {
        Path lockFile = getSessionLockFile();
        FileChannel channel = null;
        try {
            channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            channel.write(ByteBuffer.wrap("\u2603".getBytes(StandardCharsets.UTF_8)));
            channel.force(true);
            FileLock fileLock = channel.tryLock();
            if (fileLock != null) {
                return channel;
            } else {
                IOUtils.closeQuietly(channel);
                throw new WorldLockedException("The world " + getFile() + " has been locked");
            }
        } catch (IOException e) {
            IOUtils.closeQuietly(channel);
            throw new WorldLockedException(e);
        }
    }

    public void writeLevelDat(CompoundTag nbt) throws IOException {
        if (!Files.isDirectory(file)) {
            throw new IOException("Not a valid world directory");
        }

        FileUtils.saveSafely(getLevelDatFile(), os -> {
            try (OutputStream gos = new GZIPOutputStream(os)) {
                NBTIO.writeTag(gos, nbt);
            }
        });
    }

    private static CompoundTag parseLevelDat(Path path) throws IOException {
        try (InputStream is = new GZIPInputStream(Files.newInputStream(path))) {
            Tag nbt = NBTIO.readTag(is);
            if (nbt instanceof CompoundTag compoundTag) {
                return compoundTag;
            } else {
                throw new IOException("level.dat malformed");
            }
        }
    }

    private static boolean isLocked(Path sessionLockFile) {
        try (FileChannel fileChannel = FileChannel.open(sessionLockFile, StandardOpenOption.WRITE)) {
            return fileChannel.tryLock() == null;
        } catch (AccessDeniedException | OverlappingFileLockException accessDeniedException) {
            return true;
        } catch (NoSuchFileException noSuchFileException) {
            return false;
        } catch (IOException e) {
            LOG.warning("Failed to open the lock file " + sessionLockFile, e);
            return false;
        }
    }

    public static List<World> getWorlds(Path savesDir) {
        if (Files.exists(savesDir)) {
            try (Stream<Path> stream = Files.list(savesDir)) {
                return stream.flatMap(world -> {
                    try {
                        return Stream.of(new World(world.toAbsolutePath().normalize()));
                    } catch (IOException e) {
                        LOG.warning("Failed to read world " + world, e);
                        return Stream.empty();
                    }
                }).toList();
            } catch (IOException e) {
                LOG.warning("Failed to read saves", e);
            }
        }
        return List.of();
    }

    @Override
    public String toString() {
        return "World" + (isLocked() ? " (Locked) " : "") +
                "{name='" + getWorldName() + "'" +
                ",seed=" + getSeed() +
                "}";
    }

    /**
     * @author Xiaotian
     * @see <a href="https://minecraft.wiki/w/Region_file_format">The Region file format</a>
     */
    public static class WorldParser {

        private static final int SECTOR_SIZE = 4096;
        private static final int HEADER_SIZE = 8192;

        private static final int COMPRESSION_ZLIB = 2;
        private static final int COMPRESSION_LZ4 = 4;

        public final WorldPath overworld;
        public final WorldPath the_nether;
        public final WorldPath the_end;

        private final ConcurrentHashMap<Chunk, byte[]> chunkCache = new ConcurrentHashMap<>();
        private final @NotNull World world;

        public WorldParser(@NotNull World world) {
            LOG.info("Parsing world(%s)[%s]".formatted(world.getGameVersion(), world.getWorldName()));

            this.world = world;

            if (Objects.requireNonNull(world.getGameVersion()).isAtLeast("26.1", "26.1-snapshot-6")) {
                Path vanillaWorldPathRoot = world.getFile().resolve("dimensions/minecraft");

                overworld = new WorldPath(
                        Files.exists(vanillaWorldPathRoot.resolve("overworld")) ? vanillaWorldPathRoot.resolve("overworld") : null,
                        "overworld"
                );
                the_nether = new WorldPath(
                        Files.exists(vanillaWorldPathRoot.resolve("the_nether")) ? vanillaWorldPathRoot.resolve("the_nether") : null,
                        "the_nether"
                );
                the_end = new WorldPath(
                        Files.exists(vanillaWorldPathRoot.resolve("the_end")) ? vanillaWorldPathRoot.resolve("the_end") : null,
                        "the_end"
                );
            } else {
                overworld = new WorldPath(world.getFile(), "overworld");
                the_nether = new WorldPath(
                        Files.exists(world.getFile().resolve("DIM-1")) ? world.getFile().resolve("DIM-1") : null,
                        "the_nether"
                );
                the_end = new WorldPath(
                        Files.exists(world.getFile().resolve("DIM1")) ? world.getFile().resolve("DIM1") : null,
                        "the_end"
                );
            }
        }

        public byte[] parseChunk(int chunkX, int chunkZ, WorldPath worldPath) throws RuntimeException {
            try {
                int regionX = chunkX >> 5;
                int regionZ = chunkZ >> 5;
                int localX = chunkX & 0x1F;
                int localZ = chunkZ & 0x1F;

                String regionFile = String.format("r.%d.%d.mca", regionX, regionZ);
                Path regionPath = worldPath.get().resolve(Paths.get("region", regionFile));

                if (!Files.exists(regionPath)) {
                    throw new RuntimeException("Region file does not exists.");
                }

                byte[] header = Files.readAllBytes(regionPath);
                if (header.length < HEADER_SIZE) {
                    throw new RuntimeException("Broken file head.");
                }

                int blockIndex = localX + 32 * localZ;
                int headerOffset = blockIndex * 4;

                int sectorOffset = ((header[headerOffset] & 0xFF) << 16)
                        | ((header[headerOffset + 1] & 0xFF) << 8)
                        | (header[headerOffset + 2] & 0xFF);

                int sectorCount = header[headerOffset + 3] & 0xFF;

                if (sectorOffset == 0 || sectorCount == 0) {
                    return new byte[] {};
                }

                int dataOffset = sectorOffset * SECTOR_SIZE;
                int compressionType = header[dataOffset + 4] & 0xFF;
                if (dataOffset + 5 > header.length) {
                    // 数据可能在额外文件中
                    return readFromExternalFile(chunkX, chunkZ, compressionType, worldPath);
                }

                int dataLength = ((header[dataOffset] & 0xFF) << 24)
                        | ((header[dataOffset + 1] & 0xFF) << 16)
                        | ((header[dataOffset + 2] & 0xFF) << 8)
                        | (header[dataOffset + 3] & 0xFF);

                if ((compressionType & 0x80) != 0) {
                    return readFromExternalFile(chunkX, chunkZ, compressionType, worldPath);
                }

                byte[] compressedData = Files.readAllBytes(regionPath);
                if (dataOffset + 5 + dataLength > compressedData.length) {
                    throw new RuntimeException("Illegal chunk data");
                }

                byte[] chunkData = decompressData(
                        Arrays.copyOfRange(compressedData, dataOffset + 5, dataOffset + 5 + dataLength),
                        compressionType
                );

                Chunk chunk = new Chunk(chunkX, chunkZ, worldPath);
                chunkCache.put(chunk, chunkData);

                return chunkData;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public String parseBlockFromChunkData(int chunkX, int chunkZ, int x, int y, int z, WorldPath worldPath) {
            Chunk chunk = new Chunk(chunkX, chunkZ, worldPath);
            byte[] data = null;
            if (!chunkCache.containsKey(chunk)) {
                data = parseChunk(chunkX, chunkZ, worldPath);
            }
            return parseBlockFromChunkData(data == null ? chunkCache.get(chunk) : data, x, y, z);
        }

        /*
        * @throws EOFException if chunk data is not found or not generated by MC
        */
        public String parseBlockFromChunkData(@NotNull Chunk chunk, int x, int y, int z) {
            byte[] data = null;
            if (!chunkCache.containsKey(chunk)) {
                data = parseChunk(chunk.x, chunk.z, chunk.world);
            }
            return parseBlockFromChunkData(data == null ? chunkCache.get(chunk) : data, x, y, z);
        }

        public String parseBlockFromChunkData(byte[] chunkData, int x, int y, int z) {
            try {
                return parseBlockFromNBT(NBTIO.readTag(new ByteArrayInputStream(chunkData)), x, y, z);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] readFromExternalFile(int chunkX, int chunkZ, int compressionType, WorldPath worldPath) {
            try {
                String externalFile = String.format("-%d.%d.mcc", chunkX, chunkZ);
                Path externalPath = worldPath.get().resolve(Paths.get("region", externalFile));

                if (!Files.exists(externalPath)) {
                    throw new RuntimeException("External region file not found.");
                }

                byte[] externalData = Files.readAllBytes(externalPath);
                return decompressData(removeHeader(externalData), compressionType);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        private byte[] decompressData(byte[] data, int compressionType) throws Exception {
            return switch (compressionType) {
                case COMPRESSION_ZLIB -> decompressZlib(data);
                case 3 -> data;
                default -> throw new UnsupportedOperationException("Unsupported compression: " + compressionType);
            };
        }

        private byte @NotNull [] decompressZlib(byte[] data) throws Exception {
            Inflater inflater = new Inflater();
            inflater.setInput(data);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length * 2);
            byte[] buffer = new byte[1024];

            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }

            outputStream.close();
            return outputStream.toByteArray();
        }

        private byte @NotNull [] removeHeader(byte @NotNull [] data) {
            return Arrays.copyOfRange(data, 4, data.length);
        }

        private @Nullable String parseBlockFromNBT(@NotNull Tag chunkData, int x, int y, int z) {
            if (!(chunkData instanceof CompoundTag chunk)) {
                return null;
            }

            x &= 15;
            z &= 15;

            if (!chunk.contains("sections")) {
                return null;
            }

            ListTag sections = chunk.get("sections");
            if (sections.size() <= 0) {
                return null;
            }

            int sectionY = y >> 4;
            int localY = y & 15;

            for (int i = 0; i < sections.size(); i++) {
                Tag sectionTag = sections.get(i);
                if (!(sectionTag instanceof CompoundTag section)) {
                    continue;
                }

                if (!section.contains("Y") || !(section.get("Y") instanceof ByteTag)) {
                    continue;
                }

                byte sectionYValue = ((ByteTag) section.get("Y")).getValue();
                if (sectionYValue != sectionY) {
                    continue;
                }

                if (!section.contains("block_states") || !(section.get("block_states") instanceof CompoundTag blockStates)) {
                    return "minecraft:air";
                }

                if (!blockStates.contains("palette") || !(blockStates.get("palette") instanceof ListTag palette)) {
                    return "minecraft:air";
                }

                if (palette.size() <= 0) {
                    return "minecraft:air";
                }

                if (!blockStates.contains("data") || !(blockStates.get("data") instanceof LongArrayTag dataArray)) {
                    Tag firstBlock = palette.get(0);
                    if (firstBlock instanceof CompoundTag blockEntry) {
                        if (blockEntry.contains("Name") && blockEntry.get("Name") instanceof StringTag nameTag) {
                            return nameTag.getValue();
                        }
                    }
                    return "minecraft:air";
                }

                long[] data = dataArray.getValue();
                int index3D = localY * 256 + z * 16 + x;
                int longIndex = index3D >> 6;
                int bitOffset = (index3D & 63);

                if (longIndex >= data.length) {
                    return "minecraft:air";
                }

                long value = data[longIndex];
                int paletteIndex = (int) ((value >>> bitOffset) & 0x3F);

                if (paletteIndex >= palette.size()) {
                    paletteIndex = 0;
                }

                Tag blockTag = palette.get(paletteIndex);
                if (blockTag instanceof CompoundTag blockEntry) {
                    if (blockEntry.contains("Name") && blockEntry.get("Name") instanceof StringTag nameTag) {
                        return nameTag.getValue();
                    }
                }

                return "minecraft:air";
            }
            return "minecraft:air";
        }

        public WorldPath getOverworld() {
            return overworld;
        }

        public WorldPath getThe_nether() {
            return the_nether;
        }

        public WorldPath getThe_end() {
            return the_end;
        }

        @Override
        public String toString() {
            return world.toString();
        }

        public record Chunk(int x, int z, WorldPath world) {
            
            @Override
            public boolean equals(Object o) {
                if (o instanceof Chunk chunk) {
                    return chunk.x == x && chunk.z == z && chunk.world.equals(world);
                }
                return false;
            }
        
            @Override
            public int hashCode() {
                return Objects.hash(x, z, world);
            }
        }

        public record WorldPath(Path worldPath, String worldType) {
            
            public Path get() {
                return worldPath;
            }
            
            @Override
            public @NotNull String toString() {
                return String.format("WorldPath<%s>{%s}", worldType, worldPath);
            }
        }
    }
}
