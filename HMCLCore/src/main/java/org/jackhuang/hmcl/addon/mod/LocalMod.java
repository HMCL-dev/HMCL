// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod;

import java.util.HashSet;
import java.util.Objects;

public class LocalMod {

    private final String id;
    private final ModLoaderType modLoaderType;
    private final HashSet<LocalModFile> files = new HashSet<>();
    private final HashSet<LocalModFile> oldFiles = new HashSet<>();

    public LocalMod(String id, ModLoaderType modLoaderType) {
        this.id = id;
        this.modLoaderType = modLoaderType;
    }

    public String getId() {
        return id;
    }

    public ModLoaderType getModLoaderType() {
        return modLoaderType;
    }

    public HashSet<LocalModFile> getFiles() {
        return files;
    }

    public HashSet<LocalModFile> getOldFiles() {
        return oldFiles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalMod localMod = (LocalMod) o;
        return Objects.equals(id, localMod.id) && modLoaderType == localMod.modLoaderType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, modLoaderType);
    }
}
