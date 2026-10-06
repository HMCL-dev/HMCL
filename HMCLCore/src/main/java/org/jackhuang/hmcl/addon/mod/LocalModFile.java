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
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jackhuang.hmcl.addon.LocalAddonManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

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
    private final BooleanProperty activeProperty;
    private final BooleanProperty corruptProperty;

    /// UI mirror of [#integrityCheckFailed], written on the FX thread so listeners can observe
    /// changes. It can lag the authoritative field by one dispatch, and is written inline when
    /// no FX toolkit is running.
    private final BooleanProperty integrityCheckFailedProperty = new SimpleBooleanProperty(this, "integrityCheckFailed", true);

    /// Whether this mod is corrupt. Written by integrity check tasks and read by any thread.
    private volatile boolean corrupt;

    /// Whether the integrity check for this mod failed to complete.
    ///
    /// This is the authoritative value read by [#isIntegrityCheckFailed()]. It starts as `true`:
    /// a newly loaded entry has not been checked yet, and until the check finishes its state is
    /// unknown, not "sound". [ModManager] clears this once a check produces a verdict.
    private volatile boolean integrityCheckFailed = true;

    /// The file's identity when this entry was loaded, or `null` if it has not been supplied.
    ///
    /// Set by [ModManager] from the directory scan, so the file's metadata is read once per
    /// scan rather than once per constructed entry. Used to detect an in-place replacement of
    /// the file under the same name.
    private volatile @Nullable ModManager.FileInfo fileInfoAtLoad;

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description) {
        this(modManager, mod, file, name, description, "", "", "", "", "");
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        this(modManager, mod, file, name, description, authors, version, gameVersion, url, logoPath, false);
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath, boolean corrupt) {
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
        this.corrupt = corrupt;
        this.corruptProperty = new SimpleBooleanProperty(this, "corrupt", corrupt);

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

    public BooleanProperty activeProperty() {
        return activeProperty;
    }

    /// Returns whether this mod file is corrupt (ZIP structure is damaged, or a checksum
    /// mismatch was found in strict mode).
    ///
    /// Reads the value written by the integrity check task directly, so the result is
    /// visible to any thread as soon as the check completes. The [corruptProperty] is
    /// updated separately for UI notification.
    public boolean isCorrupt() {
        return corrupt;
    }

    /// Returns the corrupt property for binding.
    ///
    /// The returned property is read-only: writes must go through [#setCorrupt(boolean)] so the
    /// field backing [#isCorrupt()] stays in sync. The property is updated on the FX thread and
    /// exists for UI binding only; use [#isCorrupt()] for programmatic reads.
    public ReadOnlyBooleanProperty corruptProperty() {
        return corruptProperty;
    }

    /// Sets the corrupt status of this mod file.
    ///
    /// Package-private: the corrupt verdict is owned by the integrity check pipeline in
    /// [ModManager], and letting arbitrary callers set it would let them contradict the check.
    ///
    /// This method is thread-safe and can be called from any thread. The field is the
    /// authoritative value read by [#isCorrupt()]; the property mirrors it for the UI and is
    /// written on the FX thread.
    void setCorrupt(boolean corrupt) {
        this.corrupt = corrupt;
        if (javafx.application.Platform.isFxApplicationThread()) {
            this.corruptProperty.set(corrupt);
        } else {
            javafx.application.Platform.runLater(() -> this.corruptProperty.set(corrupt));
        }
    }

    /// Returns whether the integrity check for this mod failed to complete.
    ///
    /// A failed check is distinct from a passing check: the mod's status is unknown rather
    /// than known-good. A freshly loaded mod starts in this state until its check completes,
    /// so it is never presented as verified before it has actually been checked.
    public boolean isIntegrityCheckFailed() {
        return integrityCheckFailed;
    }

    /// Marks whether the integrity check for this mod failed to complete.
    ///
    /// Package-private, for the same reason as [#setCorrupt(boolean)].
    ///
    /// This method is thread-safe and can be called from any thread. The field is the
    /// authoritative value read by [#isIntegrityCheckFailed()]; the property mirrors it for
    /// the UI and is written on the FX thread.
    ///
    /// @param failed whether the check failed
    void setIntegrityCheckFailed(boolean failed) {
        this.integrityCheckFailed = failed;
        if (javafx.application.Platform.isFxApplicationThread()) {
            this.integrityCheckFailedProperty.set(failed);
        } else {
            javafx.application.Platform.runLater(() -> this.integrityCheckFailedProperty.set(failed));
        }
    }

    /// Returns the integrity-check-failed property for binding.
    ///
    /// The returned property is read-only: writes must go through
    /// [#setIntegrityCheckFailed(boolean)] so the field backing [#isIntegrityCheckFailed()]
    /// stays in sync. The property is updated on the FX thread and exists for UI binding only.
    public ReadOnlyBooleanProperty integrityCheckFailedProperty() {
        return integrityCheckFailedProperty;
    }

    /// Returns the file's identity at the time this entry was loaded.
    ///
    /// Used to detect an in-place replacement of the file under the same name.
    ///
    /// @return the file identity, or `null` if it has not been supplied
    @Nullable ModManager.FileInfo getFileInfo() {
        return fileInfoAtLoad;
    }

    /// Records the file's identity as read by the directory scan.
    ///
    /// Called by [ModManager] when the entry is added, so the metadata does not have to be
    /// read a second time here.
    ///
    /// @param fileInfo the file's identity
    void setFileInfo(ModManager.FileInfo fileInfo) {
        this.fileInfoAtLoad = fileInfo;
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
        RemoteAddonRepository repository = source.getRepoForType(RemoteAddon.Type.MOD);
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
        return obj instanceof LocalModFile && Objects.equals(getFileName(), ((LocalModFile) obj).getFileName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileName());
    }
}
