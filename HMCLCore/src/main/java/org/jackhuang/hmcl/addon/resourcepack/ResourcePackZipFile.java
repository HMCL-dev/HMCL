// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.resourcepack;

import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.meta.PackMcMeta;
import org.jackhuang.hmcl.addon.update.AddonUpdateConditions;
import org.jackhuang.hmcl.util.function.ExceptionalBiFunction;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {

    private final PackMcMeta.PackInfo info;

    public ResourcePackZipFile(ResourcePackManager manager, Path path) throws IOException {
        super(manager, path);

        PackMcMeta meta = null;
        try (var zipFileTree = CompressingUtils.openZipTree(path)) {
            try {
                meta = PackMcMeta.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"));
            } catch (Exception e) {
                LOG.warning("Failed to parse resource pack meta", e);
            }
        }
        this.info = meta != null ? meta.pack() : null;
    }

    @Override
    public PackMcMeta.PackInfo getPackInfo() {
        return info;
    }

    @Override
    public @Nullable Image loadIcon(ExceptionalBiFunction<InputStream, String, Image, Exception> decoder) {
        try (var zipFileTree = CompressingUtils.openZipTree(getFile())) {
            @Nullable var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry == null) {
                return null;
            }

            try (var inputStream = zipFileTree.getInputStream(iconEntry)) {
                return decoder.apply(inputStream, "pack.png");
            }
        } catch (Exception e) {
            LOG.warning("Failed to load resource pack icon", e);
            return null;
        }
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    protected AddonUpdateConditions getTargetVersionRestrictions() {
        return new AddonUpdateConditions();
    }

    @Override
    public void onUpdated(String newFileNameWithExt) {
        super.onUpdated(newFileNameWithExt);
        manager.rename(getFileNameWithExtension(), newFileNameWithExt);
    }
}

