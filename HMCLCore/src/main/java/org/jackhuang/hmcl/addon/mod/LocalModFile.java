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
package org.jackhuang.hmcl.addon.mod;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 *
 * @author huangyuhui
 */
public final class LocalModFile extends LocalAddonFile implements Comparable<LocalModFile> {

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
    private final List<String> bundledMods;
    // Full Jar-in-Jar tree (all nesting depths), scanned eagerly by ModManager after construction so
    // the dependency cascade and bundled-dependency report see the complete, accurate set.
    private List<NestedJarInspector.NestedJar> bundledTree = List.of();
    private Set<String> allBundledModIds; // flattened ids across the whole tree, computed on demand
    private final List<String> dependencies;
    private final BooleanProperty activeProperty;

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description) {
        this(modManager, mod, file, name, description, "", "", "", "", "");
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        this(modManager, mod, file, name, description, authors, version, gameVersion, url, logoPath, List.of(), List.of());
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath, List<String> bundledMods, List<String> dependencies) {
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
        this.bundledMods = bundledMods == null ? List.of() : List.copyOf(bundledMods);
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);

        activeProperty = new SimpleBooleanProperty(this, "active", !modManager.isDisabled(file)) {
            @Override
            protected void invalidated() {
                if (isOld()) return;

                Path path = LocalModFile.this.file.toAbsolutePath();

                try {
                    if (get())
                        LocalModFile.this.file = modManager.enableMod(path);
                    else
                        LocalModFile.this.file = modManager.disableMod(path);
                } catch (IOException e) {
                    LOG.error("Unable to invert state of mod file " + path, e);
                }
            }
        };

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

    public List<String> getBundledMods() {
        return bundledMods;
    }

    public boolean hasBundledMods() {
        return !bundledMods.isEmpty();
    }

    /// The full Jar-in-Jar tree (every nesting depth), with real parsed metadata for each node.
    /// Populated eagerly by {@link ModManager} during the scan; empty for mods without nested jars.
    public List<NestedJarInspector.NestedJar> getBundledTree() {
        return bundledTree;
    }

    void setBundledTree(List<NestedJarInspector.NestedJar> bundledTree) {
        this.bundledTree = bundledTree == null ? List.of() : bundledTree;
        this.allBundledModIds = null;
    }

    /// Every mod id bundled anywhere in this jar's Jar-in-Jar tree (all depths). Used by the dependency
    /// cascade and the bundled-dependency status so a dependency shipped inside a wrapper is recognized.
    public Set<String> getAllBundledModIds() {
        Set<String> ids = allBundledModIds;
        if (ids == null) {
            ids = new HashSet<>();
            NestedJarInspector.collectIds(bundledTree, ids);
            allBundledModIds = ids;
        }
        return ids;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    public BooleanProperty activeProperty() {
        return activeProperty;
    }

    public boolean isActive() {
        return activeProperty.get();
    }

    public void setActive(boolean active) {
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
    public void delete() throws IOException {
        Files.deleteIfExists(file);
    }

    @Override
    public AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        RemoteAddonRepository repository = source.getRepoForType(RemoteAddonRepository.Type.MOD);
        if (repository == null) return null;
        Optional<RemoteAddon.Version> currentVersion = repository.getRemoteVersionByLocalFile(file);
        if (currentVersion.isEmpty()) return null;
        List<RemoteAddon.Version> remoteVersions = repository.getRemoteVersionsById(downloadProvider, currentVersion.get().modid())
                .filter(version -> version.gameVersions().contains(gameVersion))
                .filter(version -> version.loaders().contains(getModLoaderType()))
                .filter(version -> version.datePublished().compareTo(currentVersion.get().datePublished()) > 0)
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(this, currentVersion.get(), remoteVersions.get(0), true);
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
