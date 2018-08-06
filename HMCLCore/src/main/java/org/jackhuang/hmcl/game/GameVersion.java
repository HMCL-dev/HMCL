/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.game;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.StringConstant;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * @author huangyuhui
 */
public final class GameVersion {
    private static Optional<String> getVersionOfClassMinecraft(ZipFile file, ZipArchiveEntry entry) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(file.getInputStream(entry), ConstantType.STRING);

        return StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
                .map(StringConstant::get)
                .filter(s -> s.startsWith("Minecraft Minecraft "))
                .map(s -> s.substring("Minecraft Minecraft ".length()))
                .findFirst();
    }

    private static Optional<String> getVersionFromClassMinecraftServer(ZipFile file, ZipArchiveEntry entry) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(file.getInputStream(entry), ConstantType.STRING);

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

        try {
            try (ZipFile gameJar = new ZipFile(file)) {
                ZipArchiveEntry minecraft = gameJar.getEntry("net/minecraft/client/Minecraft.class");
                if (minecraft != null) {
                    Optional<String> result = getVersionOfClassMinecraft(gameJar, minecraft);
                    if (result.isPresent())
                        return result;
                }
                ZipArchiveEntry minecraftServer = gameJar.getEntry("net/minecraft/server/MinecraftServer.class");
                if (minecraftServer != null)
                    return getVersionFromClassMinecraftServer(gameJar, minecraftServer);
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
