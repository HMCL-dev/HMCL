package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

final class ResourcePackZipFile extends ResourcePackFile {
    private final PackMcMeta meta;
    private final byte @Nullable [] icon;

    public ResourcePackZipFile(ResourcePackManager manager, Path path) throws IOException {
        super(manager, path);

        PackMcMeta metaTemp = null;
        byte[] iconTemp = null;

        try (var zipFileTree = new ZipFileTree(CompressingUtils.openZipFile(path))) {
            try {
                metaTemp = JsonUtils.fromNonNullJson(zipFileTree.readTextEntry("/pack.mcmeta"), PackMcMeta.class);
            } catch (Exception e) {
                LOG.warning("Failed to parse resource pack meta", e);
            }

            var iconEntry = zipFileTree.getEntry("/pack.png");
            if (iconEntry != null) {
                try (InputStream is = zipFileTree.getInputStream(iconEntry)) {
                    iconTemp = is.readAllBytes();
                } catch (Exception e) {
                    LOG.warning("Failed to load resource pack icon", e);
                }
            }
        }
        this.meta = metaTemp;
        this.icon = iconTemp;
    }

    @Override
    public PackMcMeta getMeta() {
        return meta;
    }

    @Override
    public byte @Nullable [] getIcon() {
        return icon;
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    public AddonUpdate checkUpdates(String gameVersion, RemoteMod.Type type) throws IOException {
        RemoteModRepository repository = type.getRepoForType(RemoteModRepository.Type.RESOURCE_PACK);
        if (repository == null) return null;
        Optional<RemoteMod.Version> currentVersion = repository.getRemoteVersionByLocalFile(file);
        if (currentVersion.isEmpty()) return null;
        List<RemoteMod.Version> remoteVersions = repository.getRemoteVersionsById(currentVersion.get().getModid())
                .filter(version -> version.getGameVersions().contains(gameVersion))
                .filter(version -> version.getDatePublished().compareTo(currentVersion.get().getDatePublished()) > 0)
                .sorted(Comparator.comparing(RemoteMod.Version::getDatePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(this, currentVersion.get(), remoteVersions.get(0));
    }
}

