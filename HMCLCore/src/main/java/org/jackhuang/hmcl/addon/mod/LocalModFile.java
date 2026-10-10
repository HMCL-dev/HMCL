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
import org.jetbrains.annotations.Unmodifiable;

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

    /// Current path, updated when the file is enabled or disabled.
    private volatile Path file;
    private final ModManager modManager;
    private final LocalMod mod;
    private final String name;
    private final Description description;
    private final String authors;
    private final String version;
    private final String gameVersion;
    /// Whether [#gameVersion] is a required loader constraint rather than display metadata.
    private final boolean minecraftConstraintRequired;
    private final String url;
    private final String fileName;
    /// Stable enabled-path identity retained across disabled/old suffix renames.
    private final Path identityPath;
    private final String logoPath;
    private final @Unmodifiable List<String> bundledMods;
    /// Required and optional dependency declarations with their original loader constraints.
    private final @Unmodifiable List<ModDependency> dependencies;

    /// Additional capability IDs exposed by this file, mapped to their provided versions.
    private final @Unmodifiable Map<String, String> providedVersions;

    /// Loader-declared hard and soft conflicts.
    private final @Unmodifiable List<ModConflict> conflicts;
    /// Atomically published immutable Jar-in-Jar tree.
    private volatile @Unmodifiable List<NestedJarInspector.NestedJar> bundledTree = List.of();
    private final BooleanProperty activeProperty;

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description) {
        this(modManager, mod, file, name, description, "", "", "", "", "");
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        this(modManager, mod, file, name, description, authors, version, gameVersion, url, logoPath,
                List.of(), List.of(), Map.of());
    }

    /// Creates a parsed local mod file with loader-native dependency and capability metadata.
    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description,
                        String authors, String version, String gameVersion, String url, String logoPath,
                        List<String> bundledMods, List<ModDependency> dependencies,
                        Map<String, String> providedVersions) {
        this(modManager, mod, file, name, description, authors, version, gameVersion, url, logoPath,
                bundledMods, dependencies, providedVersions, List.of());
    }

    /// Creates a parsed local mod file with complete dependencies, aliases, and conflicts.
    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description,
                        String authors, String version, String gameVersion, String url, String logoPath,
                        List<String> bundledMods, List<ModDependency> dependencies,
                        Map<String, String> providedVersions, List<ModConflict> conflicts) {
        this(modManager, mod, file, name, description, authors, version, gameVersion, url, logoPath,
                bundledMods, dependencies, providedVersions, conflicts, false);
    }

    /// Creates a parsed local mod file and marks whether its Minecraft value is a required constraint.
    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description,
                        String authors, String version, String gameVersion, String url, String logoPath,
                        List<String> bundledMods, List<ModDependency> dependencies,
                        Map<String, String> providedVersions, List<ModConflict> conflicts,
                        boolean minecraftConstraintRequired) {
        super();
        this.modManager = modManager;
        this.mod = mod;
        this.file = file;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.minecraftConstraintRequired = minecraftConstraintRequired;
        this.url = url;
        this.logoPath = logoPath;
        this.bundledMods = bundledMods == null ? List.of() : List.copyOf(bundledMods);
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.providedVersions = providedVersions == null ? Map.of() : Map.copyOf(providedVersions);
        this.conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        Path absolute = file.toAbsolutePath().normalize();
        this.identityPath = absolute.resolveSibling(
                LocalAddonManager.getLocalAddonName(absolute)).normalize();

        activeProperty = new SimpleBooleanProperty(this, "active", !modManager.isDisabled(file)) {
            /// Prevents a failed filesystem mutation rollback from recursively renaming the file.
            private boolean restoring;

            @Override
            protected void invalidated() {
                if (isOld() || restoring) return;

                Path path = LocalModFile.this.file.toAbsolutePath();

                try {
                    if (get())
                        LocalModFile.this.file = modManager.enableMod(path);
                    else
                        LocalModFile.this.file = modManager.disableMod(path);
                } catch (IOException e) {
                    LOG.error("Unable to invert state of mod file " + path, e);
                    restoring = true;
                    try {
                        set(!get());
                    } finally {
                        restoring = false;
                    }
                } finally {
                    modManager.invalidatePublishedAnalysis();
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

    /// Returns whether the Minecraft metadata must filter this file from the provider graph.
    public boolean isMinecraftConstraintRequired() {
        return minecraftConstraintRequired;
    }

    public String getUrl() {
        return url;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public @Unmodifiable List<String> getBundledMods() {
        return bundledMods;
    }

    public boolean hasBundledMods() {
        return !bundledMods.isEmpty() || !bundledTree.isEmpty();
    }

    /// The full Jar-in-Jar tree (every nesting depth), with real parsed metadata for each node.
    /// Populated by {@link ModManager}'s background scan; empty for mods without nested jars.
    public @Unmodifiable List<NestedJarInspector.NestedJar> getBundledTree() {
        return bundledTree;
    }

    /// Publishes a complete immutable Jar-in-Jar tree.
    void setBundledTree(List<NestedJarInspector.NestedJar> bundledTree) {
        this.bundledTree = List.copyOf(bundledTree);
    }

    /// Returns dependency declarations in the source loader's version dialect.
    public @Unmodifiable List<ModDependency> getDependencies() {
        return dependencies;
    }

    /// Returns additional mod IDs provided by this file and the version exposed for each ID.
    public @Unmodifiable Map<String, String> getProvidedVersions() {
        return providedVersions;
    }

    /// Returns every capability ID exposed by this file, including its primary mod ID.
    public @Unmodifiable Set<String> getProvidedIds() {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (getId() != null && !getId().isBlank()) {
            result.add(getId());
        }
        result.addAll(providedVersions.keySet());
        return Set.copyOf(result);
    }

    /// Returns loader-declared hard and soft conflicts.
    public @Unmodifiable List<ModConflict> getConflicts() {
        return conflicts;
    }

    public boolean hasDependencies() {
        return !dependencies.isEmpty();
    }

    public BooleanProperty activeProperty() {
        return activeProperty;
    }

    public boolean isActive() {
        return !modManager.isDisabled(file);
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
        modManager.invalidatePublishedAnalysis();
    }

    @Override
    public boolean keepOldFiles() {
        return true;
    }

    @Override
    public void markDisabled() throws IOException {
        file = modManager.disableMod(file);
        activeProperty.set(false);
        modManager.invalidatePublishedAnalysis();
    }

    @Override
    public void delete() throws IOException {
        Files.deleteIfExists(file);
        modManager.invalidatePublishedAnalysis();
    }

    @Override
    public AddonUpdate checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        RemoteAddonRepository repository = source.getRepository();
        if (repository == null) return null;
        Optional<RemoteAddon.Version> currentVersion = repository.getRemoteVersionByLocalFile(file);
        if (currentVersion.isEmpty()) return null;
        List<RemoteAddon.Version> remoteVersions = repository.getRemoteVersionsById(downloadProvider, currentVersion.get().projectId())
                .filter(version -> version.gameVersions().contains(gameVersion))
                .filter(version -> version.loaders().stream().anyMatch(it -> it.type() == getModLoaderType()))
                .filter(version -> version.datePublished().compareTo(currentVersion.get().datePublished()) > 0)
                .sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed())
                .toList();
        if (remoteVersions.isEmpty()) return null;
        return new AddonUpdate(source, RemoteAddon.Type.MOD, this, currentVersion.get(), remoteVersions.get(0), true);
    }

    @Override
    public int compareTo(LocalModFile o) {
        return getFileName().compareToIgnoreCase(o.getFileName());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalModFile other && identityPath.equals(other.identityPath);
    }

    @Override
    public int hashCode() {
        return identityPath.hashCode();
    }
}
