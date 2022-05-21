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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.DigestUtils.digest;
import static org.jackhuang.hmcl.util.Hex.encodeHex;

public final class MinecraftInstanceTask<T> extends Task<ModpackConfiguration<T>> {

    private final File zipFile;
    private final Charset encoding;
    private final List<String> subDirectories;
    private final File jsonFile;
    private final T manifest;
    private final String type;
    private final String name;
    private final String version;

    public MinecraftInstanceTask(File zipFile, Charset encoding, List<String> subDirectories, T manifest, ModpackProvider modpackProvider, String name, String version, File jsonFile) {
        this.zipFile = zipFile;
        this.encoding = encoding;
        this.subDirectories = subDirectories.stream().map(FileUtils::normalizePath).collect(Collectors.toList());
        this.manifest = manifest;
        this.jsonFile = jsonFile;
        this.type = modpackProvider.getName();
        this.name = name;
        this.version = version;
    }

    @Override
    public void execute() throws Exception {
        List<ModpackConfiguration.FileInformation> overrides = new ArrayList<>();

        try (FileSystem fs = CompressingUtils.readonly(zipFile.toPath()).setEncoding(encoding).build()) {
            for (String subDirectory : subDirectories) {
                Path root = fs.getPath(subDirectory);

                if (Files.exists(root))
                    Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String relativePath = root.relativize(file).normalize().toString().replace(File.separatorChar, '/');
                            overrides.add(new ModpackConfiguration.FileInformation(relativePath, encodeHex(digest("SHA-1", file))));
                            return FileVisitResult.CONTINUE;
                        }
                    });
            }
        }

        ModpackConfiguration<T> configuration = new ModpackConfiguration<>(manifest, type, name, version, overrides);
        FileUtils.writeText(jsonFile, JsonUtils.GSON.toJson(configuration));
        setResult(configuration);
    }
}
