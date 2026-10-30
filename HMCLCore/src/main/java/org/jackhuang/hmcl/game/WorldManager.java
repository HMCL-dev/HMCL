// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldManager {
    private final Object lock = new Object();
    private final Set<World> localFiles = new LinkedHashSet<>();
    private final Path savesDirectory;
    private boolean loaded = false;

    public WorldManager(Path savesDirectory) {
        this.savesDirectory = savesDirectory;
    }

    private static Path resolveRoot(FileSystem fs) throws IOException {
        Path root = fs.getPath("/");
        if (Files.isRegularFile(root.resolve("level.dat")))
            return root;

        try (Stream<Path> stream = Files.list(root)) {
            @Unmodifiable List<Path> files = stream.toList();
            if (files.size() != 1 || !Files.isDirectory(files.get(0)))
                throw new IOException("Not a valid world zip file");

            return files.get(0);
        }
    }

    public static WorldInfo getWorldInfoFromZip(Path worldZip, boolean loadIcon) throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(worldZip).build()) {
            Path root = resolveRoot(fs);
            World.LevelData levelData = World.resolveLevelData(root, "Not a valid world zip file since level.dat or special_level.dat cannot be found.");
            String name = FileUtils.getName(root.equals(fs.getPath("/")) ? worldZip : root);
            return new WorldInfo(root, name, levelData, loadIcon ? World.loadIcon(root) : null);
        }
    }

    public Path getDirectory() {
        return savesDirectory;
    }

    public void refresh() throws IOException {
        synchronized (lock) {
            localFiles.clear();

            if (Files.isDirectory(savesDirectory)) {
                try (Stream<Path> stream = Files.list(savesDirectory)) {
                    stream.filter(Files::isDirectory).forEach(world -> {
                        try {
                            localFiles.add(new World(this, world.toAbsolutePath().normalize()));
                        } catch (IOException e) {
                            LOG.warning("Failed to read world " + world, e);
                        }
                    });
                }
            }
            loaded = true;
        }
    }

    public Comparator<World> getComparator() {
        return World::compareTo;
    }

    public @Unmodifiable List<World> getLocalFiles() throws IOException {
        synchronized (lock) {
            if (!loaded)
                refresh();
            return localFiles.stream().sorted(getComparator()).toList();
        }
    }

    public void install(Path worldZip, String newName) throws IOException {
        synchronized (lock) {
            if (!loaded)
                refresh();
            Files.createDirectories(savesDirectory);

            Path worldDir = savesDirectory.resolve(newName);
            if (Files.isDirectory(worldDir))
                throw new FileAlreadyExistsException("World already exists");

            try (FileSystem fs = CompressingUtils.readonly(worldZip).build()) {
                new Unzipper(worldZip, worldDir).setSubDirectory(resolveRoot(fs).toString()).unzip();
            }

            World world = new World(this, worldDir);
            world.setWorldName(newName);
            localFiles.add(world);
        }
    }

    public void copy(World world, String newName) throws IOException {
        synchronized (lock) {
            if (world.isLocked())
                throw new WorldLockedException("The world " + world.getFile() + " has been locked");
            Path newPath = world.file.resolveSibling(newName);
            FileUtils.copyDirectory(world.file, newPath);
            World w = new World(this, newPath);
            w.setWorldName(newName);
            localFiles.add(w);
        }
    }

    public boolean delete(World world) throws IOException {
        synchronized (lock) {
            if (world.worldManager != this)
                return false;
            world.delete();
            localFiles.remove(world);
            return true;
        }
    }

    public record WorldInfo(Path file, String fileName, World.LevelData levelData, @Nullable Image icon) {
    }
}
