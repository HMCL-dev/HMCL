/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ResourcepackZipFile implements ResourcepackFile {
    private final Path path;
    private final byte @Nullable [] icon;
    private final String name;
    private final LocalModFile.Description description;

    public ResourcepackZipFile(Path path) throws IOException {
        this.path = path;
        LocalModFile.Description description = null;

        byte[] icon = null;

        try (var zipFileTree = new ZipFileTree(CompressingUtils.openZipFile(path))) {
            try {
                description = JsonUtils.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"), PackMcMeta.class).pack().description();
            } catch (Exception e) {
                LOG.warning("Failed to parse resourcepack meta", e);
            }

            var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry != null) {
                try (InputStream is = zipFileTree.getInputStream(iconEntry)) {
                    icon = is.readAllBytes();
                } catch (Exception e) {
                    LOG.warning("Failed to load resourcepack icon", e);
                }
            }
        }

        this.icon = icon;
        this.description = description;

        name = FileUtils.getNameWithoutExtension(path);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public LocalModFile.Description getDescription() {
        return description;
    }

    @Override
    public byte @Nullable [] getIcon() {
        return icon;
    }
}

