/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 *
 * @author huangyuhui
 */
public final class LocalMod implements Comparable<LocalMod> {

    private Path file;
    private final ModLoaderType modLoaderType;
    private final String id;
    private final String name;
    private final Description description;
    private final String authors;
    private final String version;
    private final String gameVersion;
    private final String url;
    private final String fileName;
    private final String logoPath;
    private final BooleanProperty activeProperty;

    public LocalMod(File file, ModLoaderType modLoaderType, String id, String name, Description description) {
        this(file, modLoaderType, id, name, description, "", "", "", "", "");
    }

    public LocalMod(File file, ModLoaderType modLoaderType, String id, String name, Description description, String authors, String version, String gameVersion, String url, String logoPath) {
        this.file = file.toPath();
        this.modLoaderType = modLoaderType;
        this.id = id;
        this.name = name;
        this.description = description;
        this.authors = authors;
        this.version = version;
        this.gameVersion = gameVersion;
        this.url = url;
        this.logoPath = logoPath;

        activeProperty = new SimpleBooleanProperty(this, "active", !ModManager.isDisabled(file)) {
            @Override
            protected void invalidated() {
                Path path = LocalMod.this.file.toAbsolutePath();

                try {
                    if (get())
                        LocalMod.this.file = ModManager.enableMod(path);
                    else
                        LocalMod.this.file = ModManager.disableMod(path);
                } catch (IOException e) {
                    Logging.LOG.log(Level.SEVERE, "Unable to invert state of mod file " + path, e);
                }
            }
        };

        fileName = StringUtils.substringBeforeLast(isActive() ? file.getName() : FileUtils.getNameWithoutExtension(file), '.');
    }

    public Path getFile() {
        return file;
    }

    public ModLoaderType getModLoaderType() {
        return modLoaderType;
    }

    public String getId() {
        return id;
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

    public ModUpdate checkUpdates(String gameVersion, RemoteModRepository repository) throws IOException {
        Optional<RemoteMod.Version> currentVersion = repository.getRemoteVersionByLocalFile(this, file);
        if (!currentVersion.isPresent()) return null;
        List<RemoteMod.Version> remoteVersions = repository.getRemoteVersionsById(currentVersion.get().getModid())
                .filter(version -> version.getGameVersions().contains(gameVersion))
                .filter(version -> version.getLoaders().contains(modLoaderType))
                .sorted(Comparator.comparing(RemoteMod.Version::getDatePublished).reversed())
                .collect(Collectors.toList());
        return new ModUpdate(this, currentVersion.get(), remoteVersions);
    }

    @Override
    public int compareTo(LocalMod o) {
        return getFileName().compareTo(o.getFileName());
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof LocalMod && Objects.equals(getFileName(), ((LocalMod) obj).getFileName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileName());
    }

    public static class ModUpdate {
        private final LocalMod localMod;
        private final RemoteMod.Version currentVersion;
        private final List<RemoteMod.Version> candidates;

        public ModUpdate(LocalMod localMod, RemoteMod.Version currentVersion, List<RemoteMod.Version> candidates) {
            this.localMod = localMod;
            this.currentVersion = currentVersion;
            this.candidates = candidates;
        }

        public LocalMod getLocalMod() {
            return localMod;
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
