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
package org.jackhuang.hmcl.addon.resourcepack;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {

    private static final UpdateConditions UPDATE_CONDITIONS = new UpdateConditions(null);

    private final PackMcMeta meta;
    private final @Nullable Image icon;

    public ResourcePackZipFile(ResourcePackManager manager, Path path) throws IOException {
        super(manager, path);

        PackMcMeta metaTemp = null;
        byte[] iconData = null;

        try (var zipFileTree = CompressingUtils.openZipTree(path)) {
            try {
                metaTemp = PackMcMeta.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"));
            } catch (Exception e) {
                LOG.warning("Failed to parse resource pack meta", e);
            }

            var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry != null) {
                try {
                    iconData = zipFileTree.readBinaryEntry(iconEntry);
                } catch (Exception e) {
                    LOG.warning("Failed to load resource pack icon", e);
                }
            }
        }
        this.meta = metaTemp;

        Image iconTemp = null;
        if (iconData != null) {
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(iconData)) {
                iconTemp = new Image(inputStream, 64, 64, true, true);
            } catch (Exception e) {
                LOG.warning("Failed to load resource pack icon", e);
            }
        }
        this.icon = iconTemp;
    }

    @Override
    public PackMcMeta getMeta() {
        return meta;
    }

    @Override
    public @Nullable Image getIcon() {
        return icon;
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    protected UpdateConditions getUpdateConditions() {
        return UPDATE_CONDITIONS;
    }

    @Override
    public void onUpdated(String newFileNameWithExt) {
        super.onUpdated(newFileNameWithExt);
        manager.rename(getFileNameWithExtension(), newFileNameWithExt);
    }
}

