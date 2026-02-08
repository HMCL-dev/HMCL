package org.jackhuang.hmcl.resourcepack;

import org.jackhuang.hmcl.mod.LocalAddonFile;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.StringUtils;
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
        if (isFileResourcePack(path)) {
            return Files.isRegularFile(path) ? new ResourcePackZipFile(manager, path) : new ResourcePackFolder(manager, path);
        }
        return null;
    }

    public static boolean isFileResourcePack(Path file) {
        return Files.exists(file) && (file.toString().toLowerCase(Locale.ROOT).endsWith(".zip") || Files.isRegularFile(file.resolve("pack.mcmeta")));
    }

    protected final ResourcePackManager manager;
    protected Path file;
    protected final String fileName;
    protected final String fileNameWithExtension;

    private Compatibility compatibility = null;

    protected ResourcePackFile(ResourcePackManager manager, Path file) {
        super(false);
        this.manager = manager;
        this.file = file;
        this.fileName = StringUtils.parseColorEscapes(FileUtils.getNameWithoutExtension(file));
        this.fileNameWithExtension = file.getFileName().toString();
    }

    @Override
    public Path getFile() {
        return file;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    public String getFileNameWithExtension() {
        return fileNameWithExtension;
    }

    public Compatibility getCompatibility() {
        if (compatibility == null) {
            compatibility = manager.getCompatibility(this);
        }
        return compatibility;
    }

    public boolean isCompatible() {
        return getCompatibility() == Compatibility.COMPATIBLE;
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
        return this.fileNameWithExtension.compareTo(other.fileNameWithExtension);
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
