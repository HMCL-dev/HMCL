// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.Unzipper;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

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
            Path root;
            if (Files.isRegularFile(fs.getPath("/level.dat"))) {
                root = fs.getPath("/");
                fileName = FileUtils.getName(file);
            } else {
                try (Stream<Path> filesStream = Files.list(fs.getPath("/"))) {
                    List<Path> files = filesStream.toList();
                    if (files.size() != 1 || !Files.isDirectory(files.get(0))) {
                        throw new IOException("Not a valid world zip file");
                    }

                    root = files.get(0);
                    fileName = FileUtils.getName(root);
                }
            }

            icon = loadIcon(root);
            levelData = resolveLevelData(root, "Not a valid world zip file since level.dat or special_level.dat cannot be found.");
        }
    }

    public void installTo(Path worldDir, String name) throws IOException {
        try (FileSystem fs = CompressingUtils.readonly(file).build()) {
            Path levelDatPath = fs.getPath("/level.dat");
            if (Files.isRegularFile(levelDatPath)) {
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
        new WorldFolder(worldDir).setWorldName(name);
    }

    @Override
    public String getFileName() {
        return fileName;
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
