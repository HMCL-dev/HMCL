package org.jackhuang.hmcl.resourcepack;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.modinfo.PackMcMeta;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public sealed abstract class ResourcePackFile implements Comparable<ResourcePackFile> permits ResourcePackFolder, ResourcePackZipFile {
    static ResourcePackFile parse(ResourcePackManager manager, Path path) throws IOException {
        String fileName = path.getFileName().toString();
        if (Files.isRegularFile(path) && fileName.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            return new ResourcePackZipFile(manager, path);
        } else if (Files.isDirectory(path) && Files.exists(path.resolve("pack.mcmeta"))) {
            return new ResourcePackFolder(manager, path);
        }
        return null;
    }

    protected final ResourcePackManager manager;
    protected final Path path;
    protected final String name;
    protected final String fileName;

    private ObjectProperty<Compatibility> compatibility = null;

    protected ResourcePackFile(ResourcePackManager manager, Path path) {
        this.manager = manager;
        this.path = path;
        this.fileName = FileUtils.getName(path);
        this.name = FileUtils.getNameWithoutExtension(path);
    }

    public Path getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public String getFileName() {
        return getPath().getFileName().toString();
    }

    public Compatibility getCompatibility() {
        if (compatibility == null) {
            compatibility = new SimpleObjectProperty<>(this, "compatibility", manager.getCompatibility(this));
        }
        return compatibility.get();
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

    @Nullable
    @Contract(pure = true)
    public abstract PackMcMeta getMeta();

    @Nullable
    public LocalModFile.Description getDescription() {
        if (getMeta() == null || getMeta().pack() == null) return null;
        return getMeta().pack().description();
    }

    public abstract byte @Nullable [] getIcon();

    public abstract void delete() throws IOException;

    @Override
    public int compareTo(@NotNull ResourcePackFile other) {
        return this.getFileName().compareToIgnoreCase(other.getFileName());
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
