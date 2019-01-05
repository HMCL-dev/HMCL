/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;

import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;

public final class MinecraftInstanceTask<T> extends Task {

    private final File zipFile;
    private final Charset encoding;
    private final String subDirectory;
    private final File jsonFile;
    private final T manifest;
    private final String type;

    public MinecraftInstanceTask(File zipFile, Charset encoding, String subDirectory, T manifest, String type, File jsonFile) {
        this.zipFile = zipFile;
        this.encoding = encoding;
        this.subDirectory = FileUtils.normalizePath(subDirectory);
        this.manifest = manifest;
        this.jsonFile = jsonFile;
        this.type = type;

        if (!zipFile.exists())
            throw new IllegalArgumentException("File " + zipFile + " does not exist. Cannot parse this modpack.");
    }

    @Override
    public void execute() throws Exception {
        List<ModpackConfiguration.FileInformation> overrides = new LinkedList<>();

        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(encoding).build()) {
            Path root = fs.getPath(subDirectory);

            if (Files.exists(root))
                Files.walkFileTree(fs.getPath(subDirectory), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String relativePath = root.relativize(file).normalize().toString();
                        overrides.add(new ModpackConfiguration.FileInformation(relativePath, encodeHex(digest("SHA-1", file))));
                        return FileVisitResult.CONTINUE;
                    }
                });
        }

        FileUtils.writeText(jsonFile, JsonUtils.GSON.toJson(new ModpackConfiguration<>(manifest, type, overrides)));
    }
}
