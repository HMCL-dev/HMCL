/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl;

import cpw.mods.modlauncher.serviceapi.ITransformerDiscoveryService;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HMCLTransformerDiscoveryService implements ITransformerDiscoveryService {
    private static final String CANDIDATES = System.getProperty("hmcl.transformer.candidates");

    @Override
    public List<Path> candidates(Path gameDirectory) {
        return Arrays.stream(CANDIDATES.split(File.pathSeparator))
                .flatMap(path -> {
                    try {
                        return Stream.of(Paths.get(path));
                    } catch (InvalidPathException e) {
                        return Stream.of();
                    }
                }).collect(Collectors.toList());
    }
}
