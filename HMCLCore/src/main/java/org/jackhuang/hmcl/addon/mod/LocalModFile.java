// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.addon.mod;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.update.AddonUpdateConditions;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 *
 * @author huangyuhui
 */
public final class LocalModFile extends LocalAddonFile<LocalModFile> {

    private Path file;
    private final ModManager modManager;
    private final LocalMod mod;
    private final String name;
    private final Description description;
    private final String authors;
    private final String version;
    private final String gameVersion;
    private final String url;
    private final String fileName;
    private final String logoPath;
    private final ReadOnlyBooleanWrapper activeProperty;

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description) {
        this(modManager, mod, file, name, description, "", "", "", "", "");
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        super();
        this.modManager = modManager;
        this.mod = mod;
        this.file = file;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.url = url;
        this.logoPath = logoPath;

        activeProperty = new ReadOnlyBooleanWrapper(this, "active", !modManager.isDisabled(file));

        fileName = FileUtils.getNameWithoutExtension(LocalAddonManager.getLocalAddonName(file));

        if (isOld()) {
            mod.getOldFiles().add(this);
        } else {
            mod.getFiles().add(this);
        }
    }

    public ModManager getModManager() {
        return modManager;
    }

    public LocalMod getMod() {
        return mod;
    }

    @Override
    public Path getFile() {
        return file;
    }

    public ModLoaderType getModLoaderType() {
        return mod.getModLoaderType();
    }

    public String getId() {
        return mod.getId();
    }

    @Override
    public String getName() {
        return name;
    }

    public Description getDescription() {
        return description;
    }

    public String getAuthors() {
        return authors;
    }

    public String getVersion() {
        return version;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public String getUrl() {
        return url;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public ReadOnlyBooleanProperty activeProperty() {
        return activeProperty.getReadOnlyProperty();
    }

    public boolean isActive() {
        return activeProperty.get();
    }

    public void setActive(boolean active) throws IOException {
        if (isOld() || active == isActive()) {
            return;
        }

        Path path = file.toAbsolutePath();
        file = active ? modManager.enableMod(path) : modManager.disableMod(path);
        activeProperty.set(active);
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    public boolean isOld() {
        return modManager.isOld(file);
    }

    @Override
    public void setOld(boolean old) throws IOException {
        file = modManager.setOld(this, old);

        if (old) {
            mod.getFiles().remove(this);
            mod.getOldFiles().add(this);
        } else {
            mod.getOldFiles().remove(this);
            mod.getFiles().add(this);
        }
    }

    @Override
    public boolean keepOldFiles() {
        return true;
    }

    @Override
    public void markDisabled() throws IOException {
        file = modManager.disableMod(file);
    }

    @Override
    public @Nullable Image loadIcon(double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        List<String> iconPaths = new ArrayList<>();

        if (StringUtils.isNotBlank(getLogoPath())) {
            iconPaths.add(getLogoPath());
        }

        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(file)) {
            for (String path : iconPaths) {
                Path iconPath = fs.getPath(path);
                if (Files.exists(iconPath)) {
                    try (var inputStream = Files.newInputStream(iconPath)) {
                        return new Image(inputStream, requestedWidth, requestedHeight, preserveRatio, smooth);
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Failed to load mod icons", e);
        }

        return null;
    }

    @Override
    protected AddonUpdateConditions getTargetVersionRestrictions() {
        return new AddonUpdateConditions(v -> v.loaders().stream().map(AddonLoader::type).collect(Collectors.toSet()).contains(getModLoaderType()));
    }

    @Override
    public int compareTo(LocalModFile o) {
        return getFileName().compareToIgnoreCase(o.getFileName());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalModFile && Objects.equals(getFileName(), ((LocalModFile) obj).getFileName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileName());
    }
}
