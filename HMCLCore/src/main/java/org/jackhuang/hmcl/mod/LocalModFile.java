/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.mod;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.Image;

import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
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
public final class LocalModFile implements Comparable<LocalModFile> {

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

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description) {
        this(modManager, mod, file, name, description, "", "", "", "", "");
    }

    public LocalModFile(ModManager modManager, LocalMod mod, Path file, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        this.modManager = modManager;
        this.mod = mod;
        this.file = file;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.url = url;
        
        String validatedLogoPath = validateLogoPath(file, logoPath);
        if (validatedLogoPath != null) {
            this.logoPath = validatedLogoPath;
        } else {
            this.logoPath = findLogoPath(file, mod.getId());
        }

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

        fileName = FileUtils.getNameWithoutExtension(ModManager.getModName(file));

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

    public boolean isActive() {
        return activeProperty.get();
    }

    public void setActive(boolean active) {
        activeProperty.set(active);
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isOld() {
        return modManager.isOld(file);
    }

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

    public void disable() throws IOException {
        file = modManager.disableMod(file);
    }

    public ModUpdate checkUpdates(String gameVersion, RemoteModRepository repository) throws IOException {
        Optional<RemoteMod.Version> currentVersion = repository.getRemoteVersionByLocalFile(this, file);
        if (!currentVersion.isPresent()) return null;
        List<RemoteMod.Version> remoteVersions = repository.getRemoteVersionsById(currentVersion.get().getModid())
                .filter(version -> version.getGameVersions().contains(gameVersion))
                .filter(version -> version.getLoaders().contains(getModLoaderType()))
                .filter(version -> version.getDatePublished().compareTo(currentVersion.get().getDatePublished()) > 0)
                .sorted(Comparator.comparing(RemoteMod.Version::getDatePublished).reversed())
                .collect(Collectors.toList());
        if (remoteVersions.isEmpty()) return null;
        return new ModUpdate(this, currentVersion.get(), remoteVersions);
    }

    @Override
    public int compareTo(LocalModFile o) {
        return getFileName().compareTo(o.getFileName());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalModFile && Objects.equals(getFileName(), ((LocalModFile) obj).getFileName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileName());
    }

    private String validateLogoPath(Path modFile, String path) {
        if (StringUtils.isBlank(path)) return null;
        
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(modFile)) {
            Path iconPath = fs.getPath(path);
            if (!Files.exists(iconPath)) return null;

            try (InputStream stream = Files.newInputStream(iconPath)) {
                Image image = new Image(stream, 40, 40, true, true);
                
                if (!image.isError() && 
                    image.getWidth() > 0 && 
                    image.getHeight() > 0 && 
                    Math.abs(image.getWidth() - image.getHeight()) < 1) {
                    return path;
                }
            } catch (Exception e) {
                LOG.warning("Failed to validate mod icon from: " + path, e);
            }
        } catch (IOException e) {
            LOG.warning("Failed to access mod file for icon validation: " + path, e);
        }
        
        return null;
    }

    private String findLogoPath(Path modFile, String modId) {
        List<String> defaultPaths = new ArrayList<>(Arrays.asList(
            "icon.png",
            "logo.png", 
            "mod_logo.png",
            "pack.png",
            "logoFile.png",
            "assets/icon.png",
            "assets/logo.png",
            "assets/mod_icon.png",
            "assets/mod_logo.png",
            "META-INF/icon.png",
            "META-INF/logo.png",
            "META-INF/mod_icon.png",
            "textures/icon.png",
            "textures/logo.png",
            "textures/mod_icon.png",
            "resources/icon.png",
            "resources/logo.png",
            "resources/mod_icon.png"
        ));

        if (StringUtils.isNotBlank(modId)) {
            defaultPaths.addAll(Arrays.asList(
                "assets/" + modId + "/icon.png",
                "assets/" + modId + "/logo.png",
                "assets/" + modId.replace("-", "") + "/icon.png",
                "assets/" + modId.replace("-", "") + "/logo.png",
                modId + ".png",
                modId + "-logo.png",
                modId + "-icon.png",
                modId + "_logo.png", 
                modId + "_icon.png",
                "textures/" + modId + "/icon.png",
                "textures/" + modId + "/logo.png",
                "resources/" + modId + "/icon.png",
                "resources/" + modId + "/logo.png"
            ));
        }

        for (String path : defaultPaths) {
            String validatedPath = validateLogoPath(modFile, path);
            if (validatedPath != null) {
                return validatedPath;
            }
        }
        
        return null;
    }

    public static class ModUpdate {
        private final LocalModFile localModFile;
        private final RemoteMod.Version currentVersion;
        private final List<RemoteMod.Version> candidates;

        public ModUpdate(LocalModFile localModFile, RemoteMod.Version currentVersion, List<RemoteMod.Version> candidates) {
            this.localModFile = localModFile;
            this.currentVersion = currentVersion;
            this.candidates = candidates;
        }

        public LocalModFile getLocalMod() {
            return localModFile;
        }

        public RemoteMod.Version getCurrentVersion() {
            return currentVersion;
        }

        public List<RemoteMod.Version> getCandidates() {
            return candidates;
        }
    }

    public static class Description {
        private final List<Part> parts;

        public Description(String text) {
            this.parts = new ArrayList<>();
            this.parts.add(new Part(text, "black"));
        }

        public Description(List<Part> parts) {
            this.parts = parts;
        }

        public List<Part> getParts() {
            return parts;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Part part : parts) {
                builder.append(part.text);
            }
            return builder.toString();
        }

        public static class Part {
            private final String text;
            private final String color;

            public Part(String text) {
                this(text, "");
            }

            public Part(String text, String color) {
                this.text = Objects.requireNonNull(text);
                this.color = Objects.requireNonNull(color);
            }

            public String getText() {
                return text;
            }

            public String getColor() {
                return color;
            }
        }
    }
}
