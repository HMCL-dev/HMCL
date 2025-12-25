package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public sealed abstract class ResourcePackFile extends LocalAddonFile implements Comparable<ResourcePackFile> permits ResourcePackFolder, ResourcePackZipFile {
    static ResourcePackFile parse(ResourcePackManager manager, Path path) throws IOException {
        String fileName = LocalFileManager.getLocalFileName(path);
        if (Files.isRegularFile(path) && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return new ResourcePackZipFile(manager, path);
        } else if (Files.isDirectory(path) && Files.exists(path.resolve("pack.mcmeta"))) {
            return new ResourcePackFolder(manager, path);
        }
        return null;
    }

    protected final ResourcePackManager manager;
    protected Path file;
    protected final String name;
    protected final String fileName;

    private Compatibility compatibility = null;

    protected ResourcePackFile(ResourcePackManager manager, Path file) {
        super(false);
        this.manager = manager;
        this.file = file;
        this.fileName = LocalFileManager.getLocalFileName(file);
        this.name = FileUtils.getNameWithoutExtension(fileName);
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getFileName() {
        return name;
    }

    public String getFileNameWithExtension() {
        return getFile().getFileName().toString();
    }

    public Compatibility getCompatibility() {
        if (compatibility == null) {
            compatibility = manager.getCompatibility(this);
        }
        return compatibility;
    }

    public boolean isEnabled() {
        return manager.isEnabled(this);
    }

    public void setEnabled(boolean enabled) {
        if (enabled) {
            manager.enableResourcePack(this);
        } else {
            manager.disableResourcePack(this);
        }
    }

    @Override
    public void setOld(boolean old) throws IOException {
        this.file = manager.setOld(this, old);
    }

    @Override
    public void markDisabled() {
    }

    @Nullable
    @Contract(pure = true)
    public abstract PackMcMeta getMeta();

    @Nullable
    public LocalModFile.Description getDescription() {
        if (getMeta() == null || getMeta().pack() == null) return null;
        return getMeta().pack().description();
    }

    public abstract byte @Nullable [] getIcon();

    @Override
    public int compareTo(@NotNull ResourcePackFile other) {
        return this.getFileNameWithExtension().compareToIgnoreCase(other.getFileNameWithExtension());
    }

    public enum Compatibility {
        COMPATIBLE,
        TOO_NEW,
        TOO_OLD,
        INVALID,
        MISSING_PACK_META,
        MISSING_GAME_META
    }
}
