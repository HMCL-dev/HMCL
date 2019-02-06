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
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.StringConstant;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author huangyuhui
 */
public final class GameVersion {
    private static Optional<String> getVersionFromJson(Path versionJson) {
        try {
            MinecraftVersion version = JsonUtils.fromNonNullJson(FileUtils.readText(versionJson), MinecraftVersion.class);
            return Optional.of(version.name);
        } catch (IOException | JsonParseException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static Optional<String> getVersionOfClassMinecraft(byte[] bytecode) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);

        return StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
                .map(StringConstant::get)
                .filter(s -> s.startsWith("Minecraft Minecraft "))
                .map(s -> s.substring("Minecraft Minecraft ".length()))
                .findFirst();
    }

    private static Optional<String> getVersionFromClassMinecraftServer(byte[] bytecode) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);

        List<String> list = StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
                .map(StringConstant::get)
                .collect(Collectors.toList());

        int idx = -1;

        for (int i = 0; i < list.size(); ++i)
            if (list.get(i).startsWith("Can't keep up!")) {
                idx = i;
                break;
            }

        for (int i = idx - 1; i >= 0; --i)
            if (list.get(i).matches(".*[0-9].*"))
                return Optional.of(list.get(i));

        return Optional.empty();
    }

    public static Optional<String> minecraftVersion(File file) {
        if (file == null || !file.exists() || !file.isFile() || !file.canRead())
            return Optional.empty();

        try (FileSystem gameJar = CompressingUtils.createReadOnlyZipFileSystem(file.toPath())) {
            Path versionJson = gameJar.getPath("version.json");
            if (Files.exists(versionJson)) {
                Optional<String> result = getVersionFromJson(versionJson);
                if (result.isPresent())
                    return result;
            }

            Path minecraft = gameJar.getPath("net/minecraft/client/Minecraft.class");
            if (Files.exists(minecraft)) {
                Optional<String> result = getVersionOfClassMinecraft(Files.readAllBytes(minecraft));
                if (result.isPresent())
                    return result;
            }
            Path minecraftServer = gameJar.getPath("net/minecraft/server/MinecraftServer.class");
            if (Files.exists(minecraftServer))
                return getVersionFromClassMinecraftServer(Files.readAllBytes(minecraftServer));
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static final class MinecraftVersion {
        public String name;

        @SerializedName("release_target")
        public String releaseTarget;

        public String id;

        public boolean stable;

        @SerializedName("world_version")
        public int worldVersion;

        @SerializedName("protocol_version")
        public int protocolVersion;

        @SerializedName("pack_version")
        public int packVersion;
    }
}
