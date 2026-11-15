// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.pack.resourcepack;

import org.jackhuang.hmcl.addon.pack.PackMcMeta;
import org.jackhuang.hmcl.addon.update.AddonUpdateConditions;
import org.jackhuang.hmcl.image.ImageLoader;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {

    public static ResourcePackZipFile load(ResourcePackManager manager, Path path) {
        PackMcMeta meta = null;
        try (var zipFileTree = CompressingUtils.openZipTree(path)) {
            meta = PackMcMeta.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"));
        } catch (Exception e) {
            LOG.warning("Failed to parse resource pack meta", e);
        }

        return new ResourcePackZipFile(manager, path, meta != null ? meta.pack() : null);
    }

    private ResourcePackZipFile(ResourcePackManager manager, Path path, PackMcMeta.PackInfo info) {
        super(manager, path, info);
    }

    @Override
    public @Nullable <I> I loadIcon(ImageLoader<I> imageLoader, int requestedWidth, int requestedHeight, boolean preserveRatio, boolean smooth) {
        try (var fs = CompressingUtils.createReadOnlyZipFileSystem(getFile())) {
            Path iconPath = fs.getPath("pack.png");
            if (Files.isRegularFile(iconPath)) {
                return imageLoader.load(iconPath, requestedWidth, requestedHeight, preserveRatio, smooth);
            }
        } catch (Exception e) {
            LOG.warning("Failed to load resource pack icon", e);
        }
        return null;
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

