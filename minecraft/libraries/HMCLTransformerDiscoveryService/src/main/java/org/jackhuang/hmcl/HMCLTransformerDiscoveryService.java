// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
