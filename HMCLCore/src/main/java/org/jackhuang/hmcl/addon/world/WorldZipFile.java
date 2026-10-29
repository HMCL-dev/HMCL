// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.world;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@NotNullByDefault
public final class WorldZipFile extends World {
    private final String fileName;
    private final @Nullable Image icon;
    private final LevelData levelData;

    public WorldZipFile(Path file) throws IOException {
        super(file);

        try (FileSystem fs = CompressingUtils.readonly(file).build()) {
            Path root = resolveRoot(fs);
            fileName = FileUtils.getName(root.equals(fs.getPath("/")) ? file : root);
            icon = loadIcon(root);
            levelData = resolveLevelData(root, "Not a valid world zip file since level.dat or special_level.dat cannot be found.");
        }
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

    public void installTo(Path worldDir, String name) throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(file).build()) {
            new Unzipper(file, worldDir).setSubDirectory(resolveRoot(fs).toString()).unzip();
        }
        new WorldFolder(worldDir).setWorldName(name);
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public void delete() throws IOException {
        Files.delete(file);
    }

    @Override
    public LevelData getLevelData() {
        return levelData;
    }

    @Override
    public @Nullable Image getIcon() {
        return icon;
    }
}
